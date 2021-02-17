package bio.terra.workspace.service.workspace.flight;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.db.ControlledResourceDao;
import bio.terra.workspace.service.job.JobMapKeys;
import bio.terra.workspace.service.resource.controlled.WsmControlledResource;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import java.util.UUID;

public class StoreControlledResourceMetadataStep implements Step {

  private final ControlledResourceDao controlledResourceDao;

  public StoreControlledResourceMetadataStep(ControlledResourceDao controlledResourceDao) {
    this.controlledResourceDao = controlledResourceDao;
  }

  @Override
  public StepResult doStep(FlightContext flightContext)
      throws InterruptedException, RetryException {
    final FlightMap inputMap = flightContext.getInputParameters();

    final WsmControlledResource resource =
        inputMap.get(JobMapKeys.REQUEST.getKeyName(), WsmControlledResource.class);
    controlledResourceDao.createControlledResource(resource.toDbModel());
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) throws InterruptedException {
    final FlightMap workingMap = flightContext.getWorkingMap();
    final UUID resourceId = workingMap.get(ControlledResourceKeys.RESOURCE_ID, UUID.class);
    final boolean deleted = controlledResourceDao.deleteControlledResource(resourceId);
    return deleted
        ? StepResult.getStepResultSuccess()
        : new StepResult(StepStatus.STEP_RESULT_FAILURE_FATAL);
  }
}
