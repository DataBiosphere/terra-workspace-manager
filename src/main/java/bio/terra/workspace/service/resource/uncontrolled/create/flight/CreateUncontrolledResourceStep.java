package bio.terra.workspace.service.resource.uncontrolled.create.flight;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.common.utils.FlightUtils;
import bio.terra.workspace.db.UncontrolledResourceDao;
import bio.terra.workspace.generated.model.CreatedDataReference;
import java.util.UUID;
import org.springframework.http.HttpStatus;

public class CreateUncontrolledResourceStep implements Step {

  private UncontrolledResourceDao uncontrolledResourceDao;

  public CreateUncontrolledResourceStep(UncontrolledResourceDao uncontrolledResourceDao) {
    this.uncontrolledResourceDao = uncontrolledResourceDao;
  }

  @Override
  public StepResult doStep(FlightContext flightContext) throws RetryException {
    FlightMap inputMap = flightContext.getInputParameters();
    UUID referenceId = inputMap.get(UncontrolledResourceFlightMapKeys.REFERENCE_ID, UUID.class);
    UUID workspaceId = inputMap.get(UncontrolledResourceFlightMapKeys.WORKSPACE_ID, UUID.class);
    String name = inputMap.get(UncontrolledResourceFlightMapKeys.NAME, String.class);
    String referenceType =
        inputMap.get(UncontrolledResourceFlightMapKeys.REFERENCE_TYPE, String.class);
    String reference = inputMap.get(UncontrolledResourceFlightMapKeys.REFERENCE, String.class);

    uncontrolledResourceDao.createUncontrolledResource(
        referenceId, workspaceId, name, referenceType, reference);

    CreatedDataReference response = new CreatedDataReference();
    response.setId(referenceId.toString());
    FlightUtils.setResponse(flightContext, response, HttpStatus.OK);

    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) {
    FlightMap inputMap = flightContext.getInputParameters();
    UUID workspaceId = inputMap.get(UncontrolledResourceFlightMapKeys.REFERENCE_ID, UUID.class);
    uncontrolledResourceDao.deleteUncontrolledResource(workspaceId);
    return StepResult.getStepResultSuccess();
  }
}
