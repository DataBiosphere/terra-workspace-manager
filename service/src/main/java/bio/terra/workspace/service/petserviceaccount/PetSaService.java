package bio.terra.workspace.service.petserviceaccount;

import bio.terra.cloudres.google.iam.ServiceAccountName;
import bio.terra.common.exception.InternalServerErrorException;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.SamRethrow;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.petserviceaccount.model.UserWithPetSa;
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
  public Policy enablePetServiceAccountImpersonation(UUID workspaceId, UserWithPetSa userAndPet) {
    // enablePetServiceAccountImpersonationWithEtag will only return an empty optional if the
    // provided eTag does not match current policy. Because we do not use eTag checking here, this
    // is always nonempty.
    return enablePetServiceAccountImpersonationWithEtag(workspaceId, userAndPet, null).get();
  }

  /**
   * Grant a user permission to impersonate their pet service account in a given workspace. Unlike
   * other operations, this does not run as a flight because it only requires one write operation.
   * This operation is idempotent.
   *
   * <p>This method requires a user's pet service account email as input. As a transitive
   * dependency, this also means the provided workspace must have a GCP context.
   *
   * <p>This method does not authenticate that the user should have access to impersonate their pet
   * SA, callers should validate this first.
   *
   * @param workspaceId ID of the workspace to enable pet SA in
   * @param userAndPet The user and the pet SA the user is being granted permission to
   * @param eTag GCP eTag which must match the pet SA's current policy. If null, this is ignored.
   * @return The new IAM policy on the user's pet service account, or empty if the eTag value
   *     provided is non-null and does not match current IAM policy on the pet SA.
   */
  public Optional<Policy> enablePetServiceAccountImpersonationWithEtag(
      UUID workspaceId, UserWithPetSa userAndPet, @Nullable String eTag) {
    String projectId = gcpCloudContextService.getRequiredGcpProject(workspaceId);
    ServiceAccountName petSaName =
        ServiceAccountName.builder().email(userAndPet.getPetEmail()).projectId(projectId).build();
    try {
      Policy saPolicy =
          crlService.getIamCow().projects().serviceAccounts().getIamPolicy(petSaName).execute();
      // If the caller supplied a non-null eTag value, it must match the current policy in order to
      // modify the policy here. Otherwise, return without modifying the policy to prevent
      // clobbering other changes.
      if (eTag != null && !saPolicy.getEtag().equals(eTag)) {
        logger.warn(
            "GCP IAM policy eTag did not match expected value when granting pet SA access for user {} in workspace {}. This is normal for Step retries.",
            userAndPet.getUserEmail(),
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
                  ImmutableList.of(
                      "user:" + userAndPet.getUserEmail(),
                      "serviceAccount:" + userAndPet.getPetEmail()));
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
    } catch (IOException e) {
      throw new InternalServerErrorException("Error enabling user's pet SA", e);
    }
  }

  /**
   * Wrapper around {@code disablePetServiceAccountImpersonationWithEtag} without requiring a
   * particular GCP eTag value.
   */
  public Optional<Policy> disablePetServiceAccountImpersonation(
      UUID workspaceId, UserWithPetSa userAndPet) {
    return disablePetServiceAccountImpersonationWithEtag(workspaceId, userAndPet, null);
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
   * @param workspaceId ID of the workspace to disable pet SA in
   * @param userAndPet The user and pet pair losing access to the pet SA
   * @param eTag GCP eTag which must match the pet SA's current policy. If null, this is ignored.
   */
  public Optional<Policy> disablePetServiceAccountImpersonationWithEtag(
      UUID workspaceId, UserWithPetSa userAndPet, @Nullable String eTag) {

    String projectId = gcpCloudContextService.getRequiredGcpProject(workspaceId);
    ServiceAccountName petServiceAccount =
        ServiceAccountName.builder().email(userAndPet.getPetEmail()).projectId(projectId).build();
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
            "GCP IAM policy eTag did not match expected value when revoking pet SA access for user {} in workspace {}. This is normal for Step retries.",
            userAndPet.getUserEmail(),
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
                      "user:" + userAndPet.getUserEmail(),
                      "serviceAccount:" + petServiceAccount.email()));
      // If no bindings exist, getBindings() returns null instead of an empty list. If there are
      // no policies, there is nothing to revoke, so this method is finished.
      List<Binding> oldBindingList = saPolicy.getBindings();
      if (oldBindingList == null) {
        return Optional.empty();
      }
      List<Binding> newBindingsList = removeBinding(oldBindingList, bindingToRemove);
      saPolicy.setBindings(newBindingsList);
      SetIamPolicyRequest request = new SetIamPolicyRequest().setPolicy(saPolicy);
      return Optional.of(
          crlService
              .getIamCow()
              .projects()
              .serviceAccounts()
              .setIamPolicy(petServiceAccount, request)
              .execute());
    } catch (IOException e) {
      throw new InternalServerErrorException("Error disabling user's pet SA", e);
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
  public Optional<ServiceAccountName> getUserPetSa(
      String projectId, String userEmail, AuthenticatedUserRequest userRequest) {
    ServiceAccountName constructedSa =
        SamRethrow.onInterrupted(
            () -> samService.constructUserPetSaEmail(projectId, userEmail, userRequest),
            "getUserPetSa");
    return serviceAccountExists(constructedSa) ? Optional.of(constructedSa) : Optional.empty();
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
      UUID workspaceId, AuthenticatedUserRequest userRequest) {
    return gcpCloudContextService
        .getGcpCloudContext(workspaceId)
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
