package bio.terra.workspace.service.petserviceaccount;

import bio.terra.cloudres.google.iam.ServiceAccountName;
import bio.terra.common.exception.ConflictException;
import bio.terra.common.exception.InternalServerErrorException;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.SamRethrow;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.workspace.GcpCloudContextService;
import bio.terra.workspace.service.workspace.model.GcpCloudContext;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.services.iam.v1.model.Binding;
import com.google.api.services.iam.v1.model.Policy;
import com.google.api.services.iam.v1.model.SetIamPolicyRequest;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
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
  private static final String SERVICE_ACCOUNT_USER_ROLE = "roles/iam.serviceAccountUser";

  private final SamService samService;
  private final GcpCloudContextService gcpCloudContextService;
  private final CrlService crlService;

  @Autowired
  public PetSaService(
      SamService samService, GcpCloudContextService gcpCloudContextService, CrlService crlService) {
    this.samService = samService;
    this.gcpCloudContextService = gcpCloudContextService;
    this.crlService = crlService;
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
    String targetMember = "group:" + proxyGroupEmail;

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
      // See if the user is already on the policy. If so, return the policy. This avoids
      // calls to set the IAM policy that have a rate limit.
      Optional<Binding> serviceAccountUserBinding = findServiceAccountUserBinding(saPolicy);
      if (serviceAccountUserBinding.isPresent()
          && serviceAccountUserBinding.get().getMembers().contains(targetMember)) {
        logger.info("user {} is already enabled on petSA {}", userToEnableEmail, petSaName.email());
        return Optional.of(saPolicy);
      } else if (serviceAccountUserBinding.isPresent()) {
        // If a binding exists for the ServiceAccountUser role but the proxy group is not a member,
        // add it.
        serviceAccountUserBinding.get().getMembers().add(targetMember);
      } else {
        // Otherwise, create the ServiceAccountUser role binding.
        Binding newBinding =
            new Binding()
                .setRole(SERVICE_ACCOUNT_USER_ROLE)
                .setMembers(ImmutableList.of(targetMember));
        // If no bindings exist, getBindings() returns null instead of an empty list.
        if (saPolicy.getBindings() != null) {
          saPolicy.getBindings().add(newBinding);
        } else {
          List<Binding> bindingList = new ArrayList<>();
          bindingList.add(newBinding);
          saPolicy.setBindings(bindingList);
        }
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
    String targetMember = "group:" + proxyGroupEmail;

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
      Optional<Binding> bindingToModify = findServiceAccountUserBinding(saPolicy);
      if (bindingToModify.isEmpty() || !bindingToModify.get().getMembers().contains(targetMember)) {
        return Optional.empty();
      }
      bindingToModify.get().getMembers().remove(targetMember);
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
    if (e instanceof GoogleJsonResponseException) {
      var g = (GoogleJsonResponseException) e;
      if (g.getStatusCode() == HttpStatus.SC_CONFLICT) {
        throw new ConflictException("Conflict " + op + " pet SA", e);
      }
    }
    throw new InternalServerErrorException(
        "Error " + op + " user's proxy group to impersonate pet SA", e);
  }

  /**
   * Find and return the IAM binding granting "roles/iam.serviceAccountUser", if one exists. Sam
   * will automatically grant pet service accounts this permission on themselves, but the proxy
   * group may or may not be a member of the binding.
   */
  private Optional<Binding> findServiceAccountUserBinding(Policy saPolicy) {
    if (saPolicy.getBindings() == null) {
      return Optional.empty();
    }
    for (Binding binding : saPolicy.getBindings()) {
      if (binding.getRole().equals(SERVICE_ACCOUNT_USER_ROLE)) {
        return Optional.of(binding);
      }
    }
    return Optional.empty();
  }

  /**
   * Returns the pet service account of a provided user in a provided project if it exists in GCP,
   * or an empty Optional if it does not.
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
