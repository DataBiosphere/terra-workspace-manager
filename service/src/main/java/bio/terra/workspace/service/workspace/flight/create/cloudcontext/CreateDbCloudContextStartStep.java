package bio.terra.workspace.service.workspace.flight.create.cloudcontext;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.workspace.db.WorkspaceDao;
import bio.terra.workspace.service.workspace.exceptions.DuplicateCloudContextException;
import bio.terra.workspace.service.workspace.model.CloudPlatform;
import java.util.UUID;

/**
 * Stores the previously generated Google Project Id in the {@link WorkspaceDao} as the Google cloud
 * context for the workspace.
 */
public class CreateDbCloudContextStartStep implements Step {
  private final UUID workspaceUuid;
  private final WorkspaceDao workspaceDao;
  private final CloudPlatform cloudPlatform;

  public CreateDbCloudContextStartStep(
      UUID workspaceUuid, WorkspaceDao workspaceDao, CloudPlatform cloudPlatform) {
    this.workspaceUuid = workspaceUuid;
    this.workspaceDao = workspaceDao;
    this.cloudPlatform = cloudPlatform;
  }

  @Override
  public StepResult doStep(FlightContext flightContext) throws InterruptedException {
    try {
      workspaceDao.createCloudContextStart(
          workspaceUuid, cloudPlatform, flightContext.getFlightId());
    } catch (DuplicateCloudContextException e) {
      // On a retry or restart, we may have already started the cloud context create,
      // so we ignore the duplicate exception.
    }
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) throws InterruptedException {
    // Delete the cloud context, but only if it is the one we created
    workspaceDao.deleteCloudContextWithFlightIdValidation(
        workspaceUuid, cloudPlatform, flightContext.getFlightId());
    return StepResult.getStepResultSuccess();
  }
}
