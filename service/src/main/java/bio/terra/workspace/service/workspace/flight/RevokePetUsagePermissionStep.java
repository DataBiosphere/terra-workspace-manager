package bio.terra.workspace.service.workspace.flight;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.petserviceaccount.PetSaService;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.PetSaKeys;
import com.google.api.services.iam.v1.model.Policy;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A step for removing a user's permission to use their pet service account in a workspace. WSM uses
 * pet service accounts to run controlled Notebook resources, so revoking access here ensures users
 * lose notebook access when they are removed from a workspace.
 */
public class RevokePetUsagePermissionStep implements Step {

  private static final Logger logger = LoggerFactory.getLogger(RevokePetUsagePermissionStep.class);

  private final UUID workspaceId;
  private final String userEmailToRemove;
  private final PetSaService petSaService;
  private final AuthenticatedUserRequest userRequest;

  public RevokePetUsagePermissionStep(
      UUID workspaceId,
      String userEmailToRemove,
      PetSaService petSaService,
      AuthenticatedUserRequest userRequest) {
    this.workspaceId = workspaceId;
    this.userEmailToRemove = userEmailToRemove;
    this.petSaService = petSaService;
    this.userRequest = userRequest;
  }

  @Override
  public StepResult doStep(FlightContext context) throws InterruptedException, RetryException {
    FlightMap workingMap = context.getWorkingMap();
    boolean userStillInWorkspace =
        workingMap.get(ControlledResourceKeys.REMOVED_USER_IS_WORKSPACE_MEMBER, Boolean.class);
    // This flight is triggered whenever a user loses any role on a workspace. If they are still
    // a member of the workspace via a group or another role, we do not need to remove their access
    // to their pet SA.
    if (userStillInWorkspace) {
      return StepResult.getStepResultSuccess();
    }
    String eTag =
        petSaService
            .disablePetServiceAccountImpersonation(workspaceId, userEmailToRemove, userRequest)
            .map(Policy::getEtag)
            .orElse(null);
    workingMap.put(PetSaKeys.MODIFIED_PET_SA_POLICY_ETAG, eTag);
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    FlightMap workingMap = context.getWorkingMap();
    boolean userStillInWorkspace =
        workingMap.get(ControlledResourceKeys.REMOVED_USER_IS_WORKSPACE_MEMBER, Boolean.class);
    // This flight is triggered whenever a user loses any role on a workspace. If they are still
    // a member of the workspace via a group or another role, we do not need to remove their access
    // to their pet SA.
    if (userStillInWorkspace) {
      return StepResult.getStepResultSuccess();
    }
    String expectedEtag = workingMap.get(PetSaKeys.MODIFIED_PET_SA_POLICY_ETAG, String.class);
    if (expectedEtag == null) {
      // If the do step did not finish, we cannot guarantee we aren't undoing something we didn't do
      logger.info(
          "Unable to undo RevokePetUsagePermissionStep for user {} in workspace {} as do step did not complete.",
          userEmailToRemove,
          workspaceId);
      return StepResult.getStepResultSuccess();
    }
    petSaService.enablePetServiceAccountImpersonationWithEtag(
        workspaceId, userEmailToRemove, expectedEtag, userRequest);
    return StepResult.getStepResultSuccess();
  }
}
