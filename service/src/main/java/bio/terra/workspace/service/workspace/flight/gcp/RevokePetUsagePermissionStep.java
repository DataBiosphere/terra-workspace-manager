package bio.terra.workspace.service.workspace.flight.gcp;

import bio.terra.cloudres.google.iam.ServiceAccountName;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.petserviceaccount.PetSaService;
import bio.terra.workspace.service.workspace.GcpCloudContextService;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.PetSaKeys;
import com.google.api.services.iam.v1.model.Policy;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A step for removing a user's permission to use their pet service account in a workspace. WSM uses
 * pet service accounts to run controlled Notebook resources, so revoking access here ensures users
 * lose notebook access when they are removed from a workspace.
 *
 * <p>There is a small window where this step may fail to undo: specifically if the do step modifies
 * the pet SA's IAM policy but fails before storing the updated eTag in the working map. This is
 * indistinguishable from a simultaneous modification by another flight, so in that case we log a
 * warning and do not undo the flight to prevent potentially clobbering another flight's changes.
 */
public class RevokePetUsagePermissionStep implements Step {

  private static final Logger logger = LoggerFactory.getLogger(RevokePetUsagePermissionStep.class);

  private final UUID workspaceUuid;
  private final String userEmailToRemove;
  private final PetSaService petSaService;
  private final GcpCloudContextService gcpCloudContextService;
  private final AuthenticatedUserRequest userRequest;

  public RevokePetUsagePermissionStep(
      UUID workspaceUuid,
      String userEmailToRemove,
      PetSaService petSaService,
      GcpCloudContextService gcpCloudContextService,
      AuthenticatedUserRequest userRequest) {
    this.workspaceUuid = workspaceUuid;
    this.userEmailToRemove = userEmailToRemove;
    this.petSaService = petSaService;
    this.gcpCloudContextService = gcpCloudContextService;
    this.userRequest = userRequest;
  }

  @Override
  public StepResult doStep(FlightContext context) throws InterruptedException, RetryException {
    FlightMap workingMap = context.getWorkingMap();
    Optional<String> validatedPetEmail = getAndValidatePet(workingMap);
    // No need to revoke pet access if the user does not have a pet in this workspace context, or
    // is not fully removed from the workspace.
    if (validatedPetEmail.isEmpty()) {
      return StepResult.getStepResultSuccess();
    }
    String updatedPolicyEtag =
        petSaService
            .disablePetServiceAccountImpersonation(workspaceUuid, userEmailToRemove, userRequest)
            .map(Policy::getEtag)
            .orElse(null);
    workingMap.put(PetSaKeys.MODIFIED_PET_SA_POLICY_ETAG, updatedPolicyEtag);
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    FlightMap workingMap = context.getWorkingMap();
    Optional<String> validatedPetEmail = getAndValidatePet(workingMap);
    // No need to revoke pet access if the user does not have a pet in this workspace context, or
    // is not fully removed from the workspace.
    if (validatedPetEmail.isEmpty()) {
      return StepResult.getStepResultSuccess();
    }
    String expectedEtag = workingMap.get(PetSaKeys.MODIFIED_PET_SA_POLICY_ETAG, String.class);
    if (expectedEtag == null) {
      // If the do step did not finish, we cannot guarantee we aren't undoing something we didn't do
      logger.info(
          "Unable to undo RevokePetUsagePermissionStep for user {} in workspace {} as do step did not complete.",
          userEmailToRemove,
          workspaceUuid);
      return StepResult.getStepResultSuccess();
    }
    petSaService.enablePetServiceAccountImpersonationWithEtag(
        workspaceUuid, userEmailToRemove, userRequest, expectedEtag);
    return StepResult.getStepResultSuccess();
  }

  /**
   * This flight is run any time a user loses a role in workspace, but this step only needs to do
   * anything under specific circumstances. Those conditions are: 1. The user has been fully removed
   * from the workspace 2. The workspace has a GCP context 3. The user has a pet SA in the workspace
   * GCP context.
   *
   * @return The email of the user's pet SA if the above conditions are met, empty otherwise.
   */
  private Optional<String> getAndValidatePet(FlightMap workingMap) {
    boolean userStillInWorkspace =
        workingMap.get(ControlledResourceKeys.REMOVED_USER_IS_WORKSPACE_MEMBER, Boolean.class);
    // This flight is triggered whenever a user loses any role on a workspace. If they are still
    // a member of the workspace via a group or another role, we do not need to remove their access
    // to their pet SA.
    if (userStillInWorkspace) {
      return Optional.empty();
    }
    // Pet service accounts only live in a GCP context. If this workspace does not have a GCP
    // context, this step does not need to do anything.
    Optional<String> maybeProjectId = gcpCloudContextService.getGcpProject(workspaceUuid);
    if (maybeProjectId.isEmpty()) {
      return Optional.empty();
    }
    // This user may not have a pet SA in this workspace. If they do not, this step does not need
    // to do anything.
    Optional<ServiceAccountName> maybePet =
        petSaService.getUserPetSa(maybeProjectId.get(), userEmailToRemove, userRequest);
    if (maybePet.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(maybePet.get().email());
  }
}
