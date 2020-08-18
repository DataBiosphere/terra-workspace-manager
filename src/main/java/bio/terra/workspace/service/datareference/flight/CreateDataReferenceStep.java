package bio.terra.workspace.service.datareference.flight;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.common.utils.FlightUtils;
import bio.terra.workspace.common.utils.MDCUtils;
import bio.terra.workspace.db.DataReferenceDao;
import bio.terra.workspace.generated.model.CreateDataReferenceRequestBody;
import bio.terra.workspace.service.job.JobMapKeys;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;

public class CreateDataReferenceStep implements Step {

  private DataReferenceDao dataReferenceDao;
  private MDCUtils mdcUtils;

  private static final String CREATE_DATA_REFERENCE_COMPLETED_KEY =
      "createDataReferenceStepCompleted";

  public CreateDataReferenceStep(DataReferenceDao dataReferenceDao, MDCUtils mdcUtils) {
    this.dataReferenceDao = dataReferenceDao;
    this.mdcUtils = mdcUtils;
  }

  @Override
  public StepResult doStep(FlightContext flightContext) throws RetryException {
    FlightMap inputMap = flightContext.getInputParameters();
    String serializedMdc = inputMap.get(DataReferenceFlightMapKeys.MDC_KEY, String.class);
    MDC.setContextMap(mdcUtils.deserializeMdcString(serializedMdc));
    FlightMap workingMap = flightContext.getWorkingMap();
    workingMap.put(CREATE_DATA_REFERENCE_COMPLETED_KEY, false);
    UUID referenceId = inputMap.get(DataReferenceFlightMapKeys.REFERENCE_ID, UUID.class);
    UUID workspaceId = inputMap.get(DataReferenceFlightMapKeys.WORKSPACE_ID, UUID.class);
    String reference = inputMap.get(DataReferenceFlightMapKeys.REFERENCE, String.class);
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
    MDC.clear();

    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) {
    FlightMap inputMap = flightContext.getInputParameters();
    FlightMap workingMap = flightContext.getWorkingMap();
    String serializedMdc = inputMap.get(DataReferenceFlightMapKeys.MDC_KEY, String.class);
    MDC.setContextMap(mdcUtils.deserializeMdcString(serializedMdc));

    if (workingMap.get(CREATE_DATA_REFERENCE_COMPLETED_KEY, Boolean.class)) {
      UUID workspaceId = inputMap.get(DataReferenceFlightMapKeys.REFERENCE_ID, UUID.class);
      dataReferenceDao.deleteDataReference(workspaceId);
    }
    MDC.clear();
    return StepResult.getStepResultSuccess();
  }
}
