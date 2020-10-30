package bio.terra.workspace.service.datareference.flight;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.common.utils.FlightUtils;
import bio.terra.workspace.db.DataReferenceDao;
import bio.terra.workspace.generated.model.CreateDataReferenceRequestBody;
import bio.terra.workspace.generated.model.DataRepoSnapshot;
import bio.terra.workspace.service.job.JobMapKeys;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;

public class CreateDataReferenceStep implements Step {

  private DataReferenceDao dataReferenceDao;

  public CreateDataReferenceStep(DataReferenceDao dataReferenceDao) {
    this.dataReferenceDao = dataReferenceDao;
  }

  @Override
  public StepResult doStep(FlightContext flightContext) throws RetryException {
    FlightMap inputMap = flightContext.getInputParameters();
    UUID referenceId = inputMap.get(DataReferenceFlightMapKeys.REFERENCE_ID, UUID.class);
    UUID workspaceId = inputMap.get(DataReferenceFlightMapKeys.WORKSPACE_ID, UUID.class);
    DataRepoSnapshot reference =
        inputMap.get(DataReferenceFlightMapKeys.REFERENCE, DataRepoSnapshot.class);
    CreateDataReferenceRequestBody body =
        inputMap.get(JobMapKeys.REQUEST.getKeyName(), CreateDataReferenceRequestBody.class);

    dataReferenceDao.createDataReference(
        referenceId,
        workspaceId,
        body.getName(),
        body.getResourceId(),
        body.getCredentialId(),
        body.getCloningInstructions(),
        body.getReferenceType(),
        reference);

    FlightUtils.setResponse(flightContext, referenceId.toString(), HttpStatus.OK);

    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) {
    FlightMap inputMap = flightContext.getInputParameters();

    UUID referenceId = inputMap.get(DataReferenceFlightMapKeys.REFERENCE_ID, UUID.class);

    try {
      dataReferenceDao.deleteDataReference(referenceId);
    } catch (DataAccessException notFoundEx) {
      // Do nothing. Because the referenceID was generated in a previous step, we can assume this is
      // the only flight working on this reference. If it does not exist, the doStep failed to
      // create it, so there's nothing to undo.
    }
    return StepResult.getStepResultSuccess();
  }
}
