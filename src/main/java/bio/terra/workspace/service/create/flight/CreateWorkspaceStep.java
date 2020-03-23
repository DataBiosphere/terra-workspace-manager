package bio.terra.workspace.service.create.flight;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.common.utils.FlightUtils;
import bio.terra.workspace.db.WorkspaceDao;
import bio.terra.workspace.generated.model.CreatedWorkspace;
import java.util.UUID;
import org.openapitools.jackson.nullable.JsonNullable;
import org.springframework.http.HttpStatus;

public class CreateWorkspaceStep implements Step {

  private WorkspaceDao workspaceDao;

  public CreateWorkspaceStep(WorkspaceDao workspaceDao) {
    this.workspaceDao = workspaceDao;
  }

  @Override
  public StepResult doStep(FlightContext flightContext) throws RetryException {
    FlightMap inputMap = flightContext.getInputParameters();
    UUID workspaceId = inputMap.get(WorkspaceFlightMapKeys.WORKSPACE_ID, UUID.class);
    // This can be null if no spend profile is specified
    JsonNullable<UUID> nullableSpendProfileId = JsonNullable.undefined();

    UUID spendProfileId = inputMap.get(WorkspaceFlightMapKeys.SPEND_PROFILE_ID, UUID.class);
    if (spendProfileId != null) {
      nullableSpendProfileId = JsonNullable.of(spendProfileId);
    }

    workspaceDao.createWorkspace(workspaceId, nullableSpendProfileId);

    CreatedWorkspace response = new CreatedWorkspace();
    response.setId(workspaceId.toString());
    FlightUtils.setResponse(flightContext, response, HttpStatus.OK);

    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) {
    FlightMap inputMap = flightContext.getInputParameters();
    UUID workspaceId = inputMap.get(WorkspaceFlightMapKeys.WORKSPACE_ID, UUID.class);
    workspaceDao.deleteWorkspace(workspaceId);
    return StepResult.getStepResultSuccess();
  }
}
