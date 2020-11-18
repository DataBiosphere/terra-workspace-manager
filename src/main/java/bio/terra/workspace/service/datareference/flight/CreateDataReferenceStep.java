package bio.terra.workspace.service.datareference.flight;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.common.exception.DuplicateDataReferenceException;
import bio.terra.workspace.common.utils.FlightUtils;
import bio.terra.workspace.db.DataReferenceDao;
import bio.terra.workspace.service.datareference.model.DataReferenceRequest;
import java.util.UUID;
import org.springframework.http.HttpStatus;

/** Stairway step to persist a data reference in WM's database. */
public class CreateDataReferenceStep implements Step {

  private DataReferenceDao dataReferenceDao;

  public CreateDataReferenceStep(DataReferenceDao dataReferenceDao) {
    this.dataReferenceDao = dataReferenceDao;
  }

  @Override
  public StepResult doStep(FlightContext flightContext) throws RetryException {
    FlightMap inputMap = flightContext.getInputParameters();
    FlightMap workingMap = flightContext.getWorkingMap();
    UUID referenceId = workingMap.get(DataReferenceFlightMapKeys.REFERENCE_ID, UUID.class);
    DataReferenceRequest request =
        inputMap.get(DataReferenceFlightMapKeys.REFERENCE, DataReferenceRequest.class);

    try {
      dataReferenceDao.createDataReference(request, referenceId);
    } catch (DuplicateDataReferenceException e) {
      // Stairway can call the same step multiple times as part of a flight, so finding a duplicate
      // reference here is fine.
    }

    FlightUtils.setResponse(flightContext, referenceId, HttpStatus.OK);

    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) {
    FlightMap inputMap = flightContext.getInputParameters();
    FlightMap workingMap = flightContext.getWorkingMap();

    UUID referenceId = workingMap.get(DataReferenceFlightMapKeys.REFERENCE_ID, UUID.class);
    UUID workspaceId = inputMap.get(DataReferenceFlightMapKeys.WORKSPACE_ID, UUID.class);

    // Ignore return value, as we don't care whether a reference was deleted or just not found.
    dataReferenceDao.deleteDataReference(workspaceId, referenceId);

    return StepResult.getStepResultSuccess();
  }
}
