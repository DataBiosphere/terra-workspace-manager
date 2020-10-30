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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;

public class CreateDataReferenceStep implements Step {

  private DataReferenceDao dataReferenceDao;

  private static final String CREATE_DATA_REFERENCE_COMPLETED_KEY =
      "createDataReferenceStepCompleted";

  private static Logger logger = LoggerFactory.getLogger(CreateDataReferenceStep.class);

  public CreateDataReferenceStep(DataReferenceDao dataReferenceDao) {
    this.dataReferenceDao = dataReferenceDao;
  }

  @Override
  public StepResult doStep(FlightContext flightContext) throws RetryException {
    FlightMap inputMap = flightContext.getInputParameters();
    FlightMap workingMap = flightContext.getWorkingMap();
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
    workingMap.put(CREATE_DATA_REFERENCE_COMPLETED_KEY, true);

    FlightUtils.setResponse(flightContext, referenceId.toString(), HttpStatus.OK);

    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) {
    FlightMap inputMap = flightContext.getInputParameters();
    FlightMap workingMap = flightContext.getWorkingMap();

    UUID workspaceId = inputMap.get(DataReferenceFlightMapKeys.REFERENCE_ID, UUID.class);
    UUID referenceId = inputMap.get(DataReferenceFlightMapKeys.REFERENCE_ID, UUID.class);

    if (workingMap.get(CREATE_DATA_REFERENCE_COMPLETED_KEY, Boolean.class) != null) {
      // In this case, the doStep completed and we should delete the reference.
      dataReferenceDao.deleteDataReference(workspaceId);
    } else {
      // In this case the doStep did not fully complete. We don't know how far into the step we got,
      // or if we created the data reference. Instead of deleting a potentially unrelated reference,
      // we just warn that there may be a new orphaned resource.
      logger.warn(
          "Undoing a CreateDataReferenceStep that was not fully completed. This may have created an orphaned reference "
              + referenceId
              + " in workspace "
              + workspaceId);
    }
    return StepResult.getStepResultSuccess();
  }
}
