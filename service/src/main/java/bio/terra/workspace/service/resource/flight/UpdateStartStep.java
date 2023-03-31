package bio.terra.workspace.service.resource.flight;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.db.ResourceDao;
import bio.terra.workspace.db.model.DbUpdater;
import bio.terra.workspace.service.resource.model.CommonUpdateParameters;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ResourceKeys;
import java.util.UUID;

public class UpdateStartStep implements Step {

  private final ResourceDao resourceDao;
  private final UUID workspaceUuid;
  private final UUID resourceId;
  private final CommonUpdateParameters commonUpdateParameters;

  public UpdateStartStep(
      ResourceDao resourceDao,
      UUID workspaceUuid,
      UUID resourceId,
      CommonUpdateParameters commonUpdateParameters) {
    this.resourceDao = resourceDao;
    this.resourceId = resourceId;
    this.workspaceUuid = workspaceUuid;
    this.commonUpdateParameters = commonUpdateParameters;
  }

  @Override
  public StepResult doStep(FlightContext flightContext)
      throws InterruptedException, RetryException {
    DbUpdater dbUpdater =
        resourceDao.updateResourceStart(
            workspaceUuid, resourceId, commonUpdateParameters, flightContext.getFlightId());
    flightContext.getWorkingMap().put(ResourceKeys.DB_UPDATER, dbUpdater);
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) throws InterruptedException {
    // Restore the resource to ready state
    resourceDao.updateResourceFailure(workspaceUuid, resourceId, flightContext.getFlightId());
    return StepResult.getStepResultSuccess();
  }
}
