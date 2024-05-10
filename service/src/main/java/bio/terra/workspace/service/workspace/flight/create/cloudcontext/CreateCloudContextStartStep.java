package bio.terra.workspace.service.workspace.flight.create.cloudcontext;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.workspace.db.WorkspaceDao;
import bio.terra.workspace.service.resource.model.WsmResourceStateRule;
import bio.terra.workspace.service.spendprofile.model.SpendProfile;
import bio.terra.workspace.service.workspace.exceptions.DuplicateCloudContextException;
import bio.terra.workspace.service.workspace.model.CloudPlatform;
import java.util.UUID;

/** Step to create the cloud context metadata in the CREATING state */
public class CreateCloudContextStartStep implements Step {
  private final UUID workspaceUuid;
  private final WorkspaceDao workspaceDao;
  private final CloudPlatform cloudPlatform;
  private final SpendProfile spendProfile;
  private final WsmResourceStateRule wsmResourceStateRule;

  public CreateCloudContextStartStep(
      UUID workspaceUuid,
      WorkspaceDao workspaceDao,
      CloudPlatform cloudPlatform,
      SpendProfile spendProfile,
      WsmResourceStateRule wsmResourceStateRule) {
    this.workspaceUuid = workspaceUuid;
    this.workspaceDao = workspaceDao;
    this.cloudPlatform = cloudPlatform;
    this.spendProfile = spendProfile;
    this.wsmResourceStateRule = wsmResourceStateRule;
  }

  @Override
  public StepResult doStep(FlightContext flightContext) throws InterruptedException {
    try {
      workspaceDao.createCloudContextStart(
          workspaceUuid, cloudPlatform, spendProfile.id(), flightContext.getFlightId());
    } catch (DuplicateCloudContextException e) {
      // On a retry or restart, we may have already started the cloud context create,
      // so we ignore the duplicate exception.
    }
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) throws InterruptedException {
    // Complete the context create in accordance with the state rule
    workspaceDao.createCloudContextFailure(
        workspaceUuid,
        cloudPlatform,
        flightContext.getFlightId(),
        flightContext.getResult().getException().orElse(null),
        wsmResourceStateRule);
    return StepResult.getStepResultSuccess();
  }
}
