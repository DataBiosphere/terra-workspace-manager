package bio.terra.workspace.service.petserviceaccount;

import bio.terra.cloudres.google.iam.ServiceAccountName;
import bio.terra.common.exception.ConflictException;
import bio.terra.common.exception.InternalServerErrorException;
import bio.terra.workspace.app.configuration.external.FeatureConfiguration;
import bio.terra.workspace.common.utils.GcpUtils;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.grant.GrantService;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.SamRethrow;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.workspace.GcpCloudContextService;
import bio.terra.workspace.service.workspace.model.GcpCloudContext;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.services.iam.v1.model.Policy;
import com.google.api.services.iam.v1.model.SetIamPolicyRequest;
import java.io.IOException;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nullable;
import org.apache.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * This component holds logic for working with pet service accounts in a workspace. Pet Service
 * accounts are a GCP-specific concept but are not resources as they are really managed by Sam.
 * However, they exist in both Sam and GCP, and WSM often needs to interact with them.
 */
@Component
public class PetSaService {

  private static final Logger logger = LoggerFactory.getLogger(PetSaService.class);

  private final SamService samService;
  private final GcpCloudContextService gcpCloudContextService;
  private final CrlService crlService;
  private final GrantService grantService;
  private final FeatureConfiguration features;

  @Autowired
  public PetSaService(
      SamService samService,
      GcpCloudContextService gcpCloudContextService,
      CrlService crlService,
      GrantService grantService,
      FeatureConfiguration features) {
    this.samService = samService;
    this.gcpCloudContextService = gcpCloudContextService;
    this.crlService = crlService;
    this.grantService = grantService;
    this.features = features;
  }

  /**
   * Wrapper around {@code enablePetServiceAccountImpersonationWithEtag} without requiring a
   * particular GCP eTag value.
   */
  public Policy enablePetServiceAccountImpersonation(
      UUID workspaceUuid, String userToEnableEmail, AuthenticatedUserRequest userRequest) {
    // enablePetServiceAccountImpersonationWithEtag will only return an empty optional if the
    // provided eTag does not match current policy. Because we do not use eTag checking here, this
    // is always nonempty.
    return enablePetServiceAccountImpersonationWithEtag(
            workspaceUuid, userToEnableEmail, userRequest, null)
        .orElseThrow(
            () -> new RuntimeException("Error enabling user's proxy group to impersonate pet SA"));
  }

  /**
   * Grant a user's proxy group permission to impersonate their pet service account in a given
   * workspace. Unlike other operations, this does not run as a flight because it only requires one
   * write operation. If the user's pet SA does not exist, this will create it. This operation is
   * idempotent.
   *
   * <p>The provided workspace must have a GCP context.
   *
   * <p>This method does not authenticate that the user should have access to impersonate their pet
   * SA, callers should validate this first.
   *
   * <p>userToEnableEmail is separate from token because of RevokePetUsagePermissionStep.undoStep().
   * If User A removes B from workspace, userToEnableEmail is B and token is from A's userRequest.
   *
   * @param workspaceUuid ID of the workspace to enable pet SA in
   * @param userToEnableEmail The user whose proxy group will be granted permission.
   * @param userReq Auth info for calling SAM. Do not use userReq.getEmail() here; it will return
   *     the caller's email, but there's no guarantee whether that will be an end-user email or a
   *     pet SA email.
   * @param eTag GCP eTag which must match the pet SA's current policy. If null, this is ignored.
   * @return The new IAM policy on the user's pet service account, or empty if the eTag value
   *     provided is non-null and does not match current IAM policy on the pet SA.
   */
  public Optional<Policy> enablePetServiceAccountImpersonationWithEtag(
      UUID workspaceUuid,
      String userToEnableEmail,
      AuthenticatedUserRequest userReq,
      @Nullable String eTag) {
    String projectId = gcpCloudContextService.getRequiredGcpProject(workspaceUuid);
    Optional<ServiceAccountName> maybePetSaName =
        getUserPetSa(projectId, userToEnableEmail, userReq);
    // If the pet SA does not exist and no eTag is specified, create the pet SA and continue.
    if (maybePetSaName.isEmpty()) {
      if (eTag == null) {
        String saEmail =
            SamRethrow.onInterrupted(
                () ->
                    samService.getOrCreatePetSaEmail(
                        gcpCloudContextService.getRequiredGcpProject(workspaceUuid),
                        userReq.getRequiredToken()),
                "enablePet");
        maybePetSaName =
            Optional.of(ServiceAccountName.builder().projectId(projectId).email(saEmail).build());
      } else {
        // If the pet SA does not exist but an eTag is specified, there's no way the eTag can match,
        // so return empty optional.
        return Optional.empty();
      }
    }
    // Pet name is populated above, so it's always safe to unwrap here.
    ServiceAccountName petSaName = maybePetSaName.get();

    String proxyGroupEmail =
        SamRethrow.onInterrupted(
            () -> samService.getProxyGroupEmail(userToEnableEmail, userReq.getRequiredToken()),
            "enablePet");
    String proxyGroupMember = GcpUtils.toGroupMember(proxyGroupEmail);

    try {
      Policy saPolicy =
          crlService.getIamCow().projects().serviceAccounts().getIamPolicy(petSaName).execute();
      // If the caller supplied a non-null eTag value, it must match the current policy in order to
      // modify the policy here. Otherwise, return without modifying the policy to prevent
      // clobbering other changes.
      if (eTag != null && !saPolicy.getEtag().equals(eTag)) {
        logger.warn(
            "GCP IAM policy eTag did not match expected value when granting pet SA access for user {} in workspace {}. This is normal for Step retries.",
            userToEnableEmail,
            workspaceUuid);
        return Optional.empty();
      }

      // Add the proxy group member to the policy. If it is already there (false), return the poliy.
      // This avoids calls to set the IAM policy that have a rate limit.
      if (!PetSaUtils.addSaMember(saPolicy, proxyGroupMember)) {
        return Optional.of(saPolicy);
      }

      // Temporary grant of user and pet to act as pet.
      if (features.isTemporaryGrantEnabled()) {
        String petSaMember = GcpUtils.toSaMember(petSaName.email());
        PetSaUtils.addSaMember(saPolicy, petSaMember);

        String userMember = null;
        if (grantService.isUserGrantAllowed(userToEnableEmail)) {
          userMember = GcpUtils.toUserMember(userToEnableEmail);
          PetSaUtils.addSaMember(saPolicy, userMember);
        }

        grantService.recordActAsGrant(workspaceUuid, userMember, petSaMember);
      }

      SetIamPolicyRequest request = new SetIamPolicyRequest().setPolicy(saPolicy);
      return Optional.of(
          crlService
              .getIamCow()
              .projects()
              .serviceAccounts()
              .setIamPolicy(petSaName, request)
              .execute());
    } catch (IOException e) {
      return handleProxyUpdateError(e, "enabling");
    }
  }

  /**
   * Wrapper around {@code disablePetServiceAccountImpersonationWithEtag} without requiring a
   * particular GCP eTag value.
   */
  public Optional<Policy> disablePetServiceAccountImpersonation(
      UUID workspaceUuid, String userEmail, AuthenticatedUserRequest userRequest) {
    return disablePetServiceAccountImpersonationWithEtag(
        workspaceUuid, userEmail, userRequest, null);
  }

  /**
   * Revoke the permission to impersonate a pet service account granted by {@code
   * enablePetServiceAccountImpersonation}. Unlike other operations, this does not run in a flight
   * because it only requires one write operation. This operation is idempotent.
   *
   * <p>This method requires a user's pet service account email as input. As a transitive
   * dependency, this also means the provided workspace must have a GCP context.
   *
   * <p>This method does not authenticate that the user should have access to impersonate their pet
   * SA, callers should validate this first.
   *
   * @param workspaceUuid ID of the workspace to disable pet SA in
   * @param userToDisableEmail The user losing access to pet SA
   * @param userRequest This request's token will be used to authenticate SAM requests
   * @param eTag GCP eTag which must match the pet SA's current policy. If null, this is ignored.
   */
  public Optional<Policy> disablePetServiceAccountImpersonationWithEtag(
      UUID workspaceUuid,
      String userToDisableEmail,
      AuthenticatedUserRequest userRequest,
      @Nullable String eTag) {
    String proxyGroupEmail =
        SamRethrow.onInterrupted(
            () -> samService.getProxyGroupEmail(userToDisableEmail, userRequest.getRequiredToken()),
            "disablePet");

    String projectId = gcpCloudContextService.getRequiredGcpProject(workspaceUuid);
    try {
      Optional<ServiceAccountName> userToDisablePetSA =
          getUserPetSa(projectId, userToDisableEmail, userRequest);
      if (userToDisablePetSA.isEmpty()) {
        return Optional.empty();
      }

      Policy saPolicy =
          crlService
              .getIamCow()
              .projects()
              .serviceAccounts()
              .getIamPolicy(userToDisablePetSA.get())
              .execute();
      // If the caller supplied a non-null eTag value, it must match the current policy in order to
      // modify the policy here. Otherwise, return without modifying the policy to prevent
      // clobbering other changes.
      if (eTag != null && !saPolicy.getEtag().equals(eTag)) {
        logger.warn(
            "GCP IAM policy eTag did not match expected value when revoking pet SA access for user {} in workspace {}. This is normal for Step retries.",
            userToDisableEmail,
            workspaceUuid);
        return Optional.empty();
      }

      // If the member is already not on the policy, we are done
      // This handles the case where there are no bindings at all, so we don't
      // need to worry about null binding later in the logic.
      if (!PetSaUtils.removeSaMember(saPolicy, GcpUtils.toGroupMember(proxyGroupEmail))) {
        return Optional.empty();
      }

      // We try to remove the pet and user as well. We do not test the features or
      // configuration. Those might have changed since we made the grants, so rather
      // than risk leaving a grant too long, we attempt to remove them. The remove member
      // code doesn't complain if the grant is not there.
      String petSaMember = GcpUtils.toSaMember(userToDisablePetSA.get().email());
      PetSaUtils.removeSaMember(saPolicy, petSaMember);
      String userMember = GcpUtils.toUserMember(userToDisableEmail);
      PetSaUtils.removeSaMember(saPolicy, userMember);

      SetIamPolicyRequest request = new SetIamPolicyRequest().setPolicy(saPolicy);
      return Optional.of(
          crlService
              .getIamCow()
              .projects()
              .serviceAccounts()
              .setIamPolicy(userToDisablePetSA.get(), request)
              .execute());
    } catch (IOException e) {
      return handleProxyUpdateError(e, "disabling");
    }
  }

  // Handle exceptions from attempting the enable or disable of pet SA impersonation
  // This always throws, but we give it a return value, so the compiler knows there
  // is no escape from the catch.
  private Optional<Policy> handleProxyUpdateError(Exception e, String op) {
    if (e instanceof GoogleJsonResponseException g) {
      if (g.getStatusCode() == HttpStatus.SC_CONFLICT) {
        throw new ConflictException("Conflict " + op + " pet SA", e);
      }
    }
    throw new InternalServerErrorException(
        "Error " + op + " user's proxy group to impersonate pet SA", e);
  }

  /**
   * Returns the pet service account of a provided user in a provided project if it exists in GCP.
   * Returns empty Optional if pet SA doesn't exist, or if userEmail is a group instead of user.
   */
  public Optional<ServiceAccountName> getUserPetSa(
      String projectId, String userEmail, AuthenticatedUserRequest userRequest) {
    Optional<ServiceAccountName> constructedSa =
        SamRethrow.onInterrupted(
            () -> samService.constructUserPetSaEmail(projectId, userEmail, userRequest),
            "getUserPetSa");

    return constructedSa.filter(sa -> serviceAccountExists(sa));
  }

  /**
   * Fetches credentials for the provided user's pet service account in the current workspace's GCP
   * context if one exists. This will create a pet SA for the user if one does not exist, but will
   * return empty if the workspace does not have a GCP context.
   *
   * <p>This method does not validate that the provided credentials have appropriate workspace
   * access.
   */
  public Optional<AuthenticatedUserRequest> getWorkspacePetCredentials(
      UUID workspaceUuid, AuthenticatedUserRequest userRequest) {
    return gcpCloudContextService
        .getGcpCloudContext(workspaceUuid)
        .map(GcpCloudContext::getGcpProjectId)
        .map(
            projectId ->
                SamRethrow.onInterrupted(
                    () -> samService.getOrCreatePetSaCredentials(projectId, userRequest),
                    "getWorkspacePetCredentials"));
  }

  /**
   * Returns whether the service account specified by a {@code ServiceAccountName} actually exists
   * on GCP.
   */
  private boolean serviceAccountExists(ServiceAccountName saName) {
    try {
      crlService.getIamCow().projects().serviceAccounts().get(saName).execute();
      return true;
    } catch (GoogleJsonResponseException googleException) {
      // If a service account does not exist, GCP will throw a 404. Any other error is unexpected
      // and should be thrown here.
      if (googleException.getStatusCode() == HttpStatus.SC_NOT_FOUND) {
        return false;
      }
      throw new InternalServerErrorException(
          "Unexpected error from GCP while checking SA", googleException);
    } catch (IOException e) {
      throw new InternalServerErrorException("Unexpected error from GCP while checking SA", e);
    }
  }
}
