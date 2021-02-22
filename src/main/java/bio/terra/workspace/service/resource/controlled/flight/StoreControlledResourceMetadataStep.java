package bio.terra.workspace.service.resource.controlled.flight;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.db.ControlledResourceDao;
import bio.terra.workspace.db.DataReferenceDao;
import bio.terra.workspace.service.datareference.flight.DataReferenceFlightMapKeys;
import bio.terra.workspace.service.job.JobMapKeys;
import bio.terra.workspace.service.resource.controlled.ControlledResource;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

public class StoreControlledResourceMetadataStep implements Step {

  private final ControlledResourceDao controlledResourceDao;
  private final DataReferenceDao dataReferenceDao;

  public StoreControlledResourceMetadataStep(
      ControlledResourceDao controlledResourceDao, DataReferenceDao dataReferenceDao) {
    this.controlledResourceDao = controlledResourceDao;
    this.dataReferenceDao = dataReferenceDao;
  }

  @Transactional
  @Override
  public StepResult doStep(FlightContext flightContext)
      throws InterruptedException, RetryException {
    final FlightMap inputMap = flightContext.getInputParameters();

    final ControlledResource resource =
        inputMap.get(JobMapKeys.REQUEST.getKeyName(), ControlledResource.class);
    final UUID resourceId = inputMap.get(ControlledResourceKeys.RESOURCE_ID, UUID.class);
    controlledResourceDao.createControlledResource(resource.toResourceDbModel(resourceId));

    final UUID referenceId = inputMap.get(DataReferenceFlightMapKeys.REFERENCE_ID, UUID.class);
    dataReferenceDao.createDataReference(resource.toDataReferenceRequest(resourceId), referenceId);
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) throws InterruptedException {
    final FlightMap workingMap = flightContext.getWorkingMap();
    final UUID resourceId = workingMap.get(ControlledResourceKeys.RESOURCE_ID, UUID.class);
    controlledResourceDao.deleteControlledResource(resourceId);
    return StepResult.getStepResultSuccess();
  }
}
