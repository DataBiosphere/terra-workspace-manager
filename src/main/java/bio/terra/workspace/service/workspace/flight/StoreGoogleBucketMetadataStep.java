package bio.terra.workspace.service.workspace.flight;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.db.ControlledResourceDao;
import bio.terra.workspace.service.job.JobMapKeys;
import bio.terra.workspace.service.resource.controlled.gcp.ControlledGcsBucketResource;
import java.util.UUID;

public class StoreGoogleBucketMetadataStep implements Step {

  private final ControlledResourceDao controlledResourceDao;

  public StoreGoogleBucketMetadataStep(ControlledResourceDao controlledResourceDao) {
    this.controlledResourceDao = controlledResourceDao;
  }

  @Override
  public StepResult doStep(FlightContext flightContext)
      throws InterruptedException, RetryException {
    final FlightMap inputMap = flightContext.getInputParameters();

    final ControlledGcsBucketResource resource =
        inputMap.get(JobMapKeys.REQUEST.getKeyName(), ControlledGcsBucketResource.class);
    controlledResourceDao.createControlledResource(resource.toDbModel());
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) throws InterruptedException {
    final FlightMap workingMap = flightContext.getWorkingMap();
    final UUID resourceId =
        workingMap.get(WorkspaceFlightMapKeys.CONTROLLED_RESOURCE_ID, UUID.class);
    final boolean deleted = controlledResourceDao.deleteControlledResource(resourceId);
    return deleted
        ? StepResult.getStepResultSuccess()
        : new StepResult(StepStatus.STEP_RESULT_FAILURE_FATAL);
  }
}
