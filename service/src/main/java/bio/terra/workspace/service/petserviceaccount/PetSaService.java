package bio.terra.workspace.service.petserviceaccount;

import bio.terra.cloudres.google.iam.ServiceAccountName;
import bio.terra.workspace.common.exception.GcpException;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.SamRethrow;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.iam.model.SamConstants;
import bio.terra.workspace.service.stage.StageService;
import bio.terra.workspace.service.workspace.GcpCloudContextService;
import bio.terra.workspace.service.workspace.WorkspaceService;
import bio.terra.workspace.service.workspace.model.Workspace;
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
import java.util.stream.Collectors;
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

  private final WorkspaceService workspaceService;
  private final StageService stageService;
  private final SamService samService;
  private final GcpCloudContextService gcpCloudContextService;
  private final CrlService crlService;

  @Autowired
  public PetSaService(
      WorkspaceService workspaceService,
      StageService stageService,
      SamService samService,
      GcpCloudContextService gcpCloudContextService,
      CrlService crlService) {
    this.workspaceService = workspaceService;
    this.stageService = stageService;
    this.samService = samService;
    this.gcpCloudContextService = gcpCloudContextService;
    this.crlService = crlService;
  }

  /**
   * Wrapper around {@code enablePetServiceAccountImpersonationWithEtag} without requiring a
   * particular GCP eTag value.
   */
  public Policy enablePetServiceAccountImpersonation(
      UUID workspaceId, String userEmailToDisable, AuthenticatedUserRequest userRequest) {
    // enablePetServiceAccountImpersonationWithEtag will only return an empty optional if the
    // provided eTag does not match current policy. Because we do not use eTag checking here, this
    // is always nonempty.
    return enablePetServiceAccountImpersonationWithEtag(
            workspaceId, userEmailToDisable, null, userRequest)
        .get();
  }
  /**
   * Grant a user permission to impersonate their pet service account in a given workspace. Unlike
   * other operations, this does not run as a flight because it only requires one write operation.
   * This operation is idempotent.
   *
   * @param workspaceId ID of the workspace to enable pet SA in
   * @param userEmailToEnable The user whose pet SA access is being granted
   * @param eTag GCP eTag which must match the pet SA's current policy. If null, this is ignored.
   * @param userRequest User credentials authorizing this call. These do not necessarily belong to
   *     the same user as userEmailToDisable, but must be owner credentials if they do not.
   * @return The new IAM policy on the user's pet service account, or empty if the eTag value
   *     provided is non-null and does not match current IAM policy on the pet SA.
   */
  public Optional<Policy> enablePetServiceAccountImpersonationWithEtag(
      UUID workspaceId,
      String userEmailToEnable,
      @Nullable String eTag,
      AuthenticatedUserRequest userRequest) {
    // Validate that the user is a member of the workspace.
    Workspace workspace = validateUserModification(workspaceId, userEmailToEnable, userRequest);
    stageService.assertMcWorkspace(workspace, "enablePet");

    String projectId = gcpCloudContextService.getRequiredGcpProject(workspaceId);
    String petSaEmail = SamRethrow.onInterrupted(() -> samService.getOrCreatePetSaEmail(projectId, userRequest), "enablePetServiceAccountImpersonationWithEtag");
    ServiceAccountName petSaName =
        ServiceAccountName.builder().email(petSaEmail).projectId(projectId).build();
    try {
      Policy saPolicy =
          crlService.getIamCow().projects().serviceAccounts().getIamPolicy(petSaName).execute();
      // If the caller supplied a non-null eTag value, it must match the current policy in order to
      // modify the policy here. Otherwise, return without modifying the policy to prevent
      // clobbering other changes.
      if (eTag != null && !saPolicy.getEtag().equals(eTag)) {
        logger.warn(
            "GCP IAM policy eTag did not match expected value when granting pet SA access for user {} in workspace {}.",
            userEmailToEnable,
            workspaceId);
        return Optional.empty();
      }
      // TODO(PF-991): In the future, the pet SA should not be included in this binding. This is a
      //  workaround to support the CLI and other applications which call the GCP Pipelines API with
      //  the pet SA's credentials.
      Binding saUserBinding =
          new Binding()
              .setRole(SERVICE_ACCOUNT_USER_ROLE)
              .setMembers(
                  ImmutableList.of("user:" + userEmailToEnable, "serviceAccount:" + petSaEmail));
      // If no bindings exist, getBindings() returns null instead of an empty list.
      List<Binding> bindingList =
          Optional.ofNullable(saPolicy.getBindings()).orElse(new ArrayList<>());
      // GCP automatically de-duplicates bindings, so this will have no effect if the user already
      // has permission to use their pet service account.
      bindingList.add(saUserBinding);
      saPolicy.setBindings(bindingList);
      SetIamPolicyRequest request = new SetIamPolicyRequest().setPolicy(saPolicy);
      return Optional.of(
          crlService
              .getIamCow()
              .projects()
              .serviceAccounts()
              .setIamPolicy(petSaName, request)
              .execute());
    } catch (GoogleJsonResponseException googleEx) {
      throw new GcpException(googleEx);
    } catch (IOException e) {
      throw new RuntimeException("Error enabling user's pet SA", e);
    }
  }

  /**
   * Wrapper around {@code disablePetServiceAccountImpersonationWithEtag} without requiring a
   * particular GCP eTag value.
   */
  public Optional<Policy> disablePetServiceAccountImpersonation(
      UUID workspaceId, String userEmailToDisable, AuthenticatedUserRequest userRequest) {
    return disablePetServiceAccountImpersonationWithEtag(
        workspaceId, userEmailToDisable, null, userRequest);
  }

  /**
   * Revoke the permission to impersonate a pet service account granted by {@code
   * enablePetServiceAccountImpersonation}. Unlike other operations, this does not run in a flight
   * because it only requires one write operation. This operation is idempotent.
   *
   * @param workspaceId ID of the workspace to disable pet SA in
   * @param userEmailToDisable The user whose pet SA access is being revoked
   * @param eTag GCP eTag which must match the pet SA's current policy. If null, this is ignored.
   * @param userRequest User credentials authorizing this call. These do not necessarily belong to
   *     the same user as userEmailToDisable, but must be owner credentials if they do not.
   */
  public Optional<Policy> disablePetServiceAccountImpersonationWithEtag(
      UUID workspaceId,
      String userEmailToDisable,
      @Nullable String eTag,
      AuthenticatedUserRequest userRequest) {
    Workspace workspace = validateUserModification(workspaceId, userEmailToDisable, userRequest);
    stageService.assertMcWorkspace(workspace, "disablePet");

    String projectId = gcpCloudContextService.getRequiredGcpProject(workspaceId);
    Optional<ServiceAccountName> maybePetSaName =
        getArbitrarySa(projectId, userEmailToDisable, userRequest);
    // The pet service account for this user may not exist in this project, in which case there is
    // no need to disable its use.
    if (maybePetSaName.isEmpty()) {
      return Optional.empty();
    }
    ServiceAccountName petServiceAccount = maybePetSaName.get();
    try {
      Policy saPolicy =
          crlService
              .getIamCow()
              .projects()
              .serviceAccounts()
              .getIamPolicy(petServiceAccount)
              .execute();
      // If the caller supplied a non-null eTag value, it must match the current policy in order to
      // modify the policy here. Otherwise, return without modifying the policy to prevent
      // clobbering other changes.
      if (eTag != null && !saPolicy.getEtag().equals(eTag)) {
        logger.warn(
            "GCP IAM policy eTag did not match expected value when revoking pet SA access for user {} in workspace {}.",
            userEmailToDisable,
            workspaceId);
        return Optional.empty();
      }
      // TODO(PF-991): when enablePetServiceAccountImpersonation stops putting the pet SA in this
      //  binding, this method should stop removing it.
      Binding bindingToRemove =
          new Binding()
              .setRole(SERVICE_ACCOUNT_USER_ROLE)
              .setMembers(
                  ImmutableList.of(
                      "user:" + userEmailToDisable, "serviceAccount:" + petServiceAccount.email()));
      // If no bindings exist, getBindings() returns null instead of an empty list. If there are
      // no policies, there is nothing to revoke, so this method is finished.
      List<Binding> oldBindingList = saPolicy.getBindings();
      if (oldBindingList == null) {
        return Optional.empty();
      }
      List<Binding> newBindingsList = removeBinding(oldBindingList, bindingToRemove);
      saPolicy.setBindings(newBindingsList);
      SetIamPolicyRequest request = new SetIamPolicyRequest().setPolicy(saPolicy);
      return Optional.of(crlService
          .getIamCow()
          .projects()
          .serviceAccounts()
          .setIamPolicy(petServiceAccount, request)
          .execute());
    } catch (GoogleJsonResponseException googleEx) {
      throw new GcpException(googleEx);
    } catch (IOException e) {
      throw new RuntimeException("Error disabling user's pet SA", e);
    }
  }

  /**
   * Check whether a user specified via an AuthenticatedUserRequest has permission to enable/disable
   * another user's usage of their pet service account. All users may enable/disable their own pet
   * SA usage, but only owners may enable/disable other users' pet SA usage.
   */
  private Workspace validateUserModification(
      UUID workspaceId, String userEmailToModify, AuthenticatedUserRequest userRequest) {
    String callerEmail =
        SamRethrow.onInterrupted(
            () -> samService.getUserEmailFromSam(userRequest), "validateUserAccess");
    if (callerEmail.equals(userEmailToModify)) {
      return workspaceService.validateWorkspaceAndAction(
          userRequest, workspaceId, SamConstants.SAM_WORKSPACE_READ_ACTION);
    } else {
      return workspaceService.validateWorkspaceAndAction(
          userRequest, workspaceId, SamConstants.SAM_WORKSPACE_OWN_ACTION);
    }
  }

  /**
   * Utility function for removing all instances of a GCP IAM binding from a list if it is present.
   * List.remove does not work here as GCP may re-order the binding's member list.
   */
  private List<Binding> removeBinding(List<Binding> bindingList, Binding bindingToRemove) {
    return bindingList.stream()
        .filter(binding -> !bindingEquals(binding, bindingToRemove))
        .collect(Collectors.toList());
  }

  private static boolean bindingEquals(Binding binding1, Binding binding2) {
    return binding1.getRole().equals(binding2.getRole())
        && binding1.getMembers().containsAll(binding2.getMembers())
        && binding2.getMembers().containsAll(binding1.getMembers());
  }

  /**
   * Returns the pet service account of a provided user in a provided project if it exists in GCP,
   * or an empty Optional if it does not.
   */
  private Optional<ServiceAccountName> getArbitrarySa(
      String projectId, String userEmail, AuthenticatedUserRequest userRequest) {
    ServiceAccountName constructedSa =
        SamRethrow.onInterrupted(() -> samService.constructArbitraryUserPetSaEmail(projectId, userEmail, userRequest), "getArbitrarySa");
    return serviceAccountExists(constructedSa) ? Optional.of(constructedSa) : Optional.empty();
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
      throw new GcpException(googleException);
    } catch (IOException e) {
      throw new RuntimeException("Unexpected error from GCP while checking SA", e);
    }
  }
}
