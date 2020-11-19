package bio.terra.workspace.service.datareference.flight;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.common.exception.DuplicateDataReferenceException;
import bio.terra.workspace.common.utils.FlightUtils;
import bio.terra.workspace.db.DataReferenceDao;
import bio.terra.workspace.service.datareference.model.CloningInstructions;
import bio.terra.workspace.service.datareference.model.DataReferenceRequest;
import bio.terra.workspace.service.datareference.model.DataReferenceType;
import bio.terra.workspace.service.datareference.model.ReferenceObject;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;

/** Stairway step to persist a data reference in WM's database. */
public class CreateDataReferenceStep implements Step {

  private DataReferenceDao dataReferenceDao;
  private ObjectMapper objectMapper;

  public CreateDataReferenceStep(DataReferenceDao dataReferenceDao, ObjectMapper objectMapper) {
    this.dataReferenceDao = dataReferenceDao;
    this.objectMapper = objectMapper;
  }

  @Override
  public StepResult doStep(FlightContext flightContext) throws RetryException {
    FlightMap inputMap = flightContext.getInputParameters();
    FlightMap workingMap = flightContext.getWorkingMap();
    UUID referenceId = workingMap.get(DataReferenceFlightMapKeys.REFERENCE_ID, UUID.class);
    UUID workspaceId = inputMap.get(DataReferenceFlightMapKeys.WORKSPACE_ID, UUID.class);
    String name = inputMap.get(DataReferenceFlightMapKeys.NAME, String.class);
    DataReferenceType type =
        inputMap.get(DataReferenceFlightMapKeys.REFERENCE_TYPE, DataReferenceType.class);
    CloningInstructions cloningInstructions =
        inputMap.get(DataReferenceFlightMapKeys.CLONING_INSTRUCTIONS, CloningInstructions.class);
    ReferenceObject referenceObject = null;
    try {
      referenceObject =
          ReferenceObject.toReferenceObject(
              type,
              objectMapper.readValue(
                  inputMap.get(DataReferenceFlightMapKeys.REFERENCE_PROPERTIES, String.class),
                  Map.class));
    } catch (JsonProcessingException ex) {
      throw new RuntimeException(
          "Error deserializing referenceObject: " + referenceObject.toString());
    }
    DataReferenceRequest request =
        DataReferenceRequest.builder()
            .workspaceId(workspaceId)
            .name(name)
            .referenceType(type)
            .cloningInstructions(cloningInstructions)
            .referenceObject(referenceObject)
            .build();

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
