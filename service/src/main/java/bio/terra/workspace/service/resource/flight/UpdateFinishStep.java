package bio.terra.workspace.service.resource.flight;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.common.utils.FlightUtils;
import bio.terra.workspace.db.ResourceDao;
import bio.terra.workspace.db.model.DbUpdater;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ResourceKeys;
import java.util.UUID;

public class UpdateFinishStep implements Step {

  private final ResourceDao resourceDao;
  private final UUID workspaceUuid;
  private final UUID resourceId;

  public UpdateFinishStep(ResourceDao resourceDao, UUID workspaceUuid, UUID resourceId) {
    this.resourceDao = resourceDao;
    this.resourceId = resourceId;
    this.workspaceUuid = workspaceUuid;
  }

  @Override
  public StepResult doStep(FlightContext flightContext)
      throws InterruptedException, RetryException {
    DbUpdater dbUpdater =
        FlightUtils.getRequired(
            flightContext.getWorkingMap(), ResourceKeys.DB_UPDATER, DbUpdater.class);
    resourceDao.updateResourceSuccess(
        workspaceUuid, resourceId, dbUpdater, flightContext.getFlightId());
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) throws InterruptedException {
    // No recovery from an error performing the database update
    return new StepResult(
        StepStatus.STEP_RESULT_FAILURE_FATAL, new RuntimeException("dismal failure"));
  }
}
