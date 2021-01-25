package bio.terra.workspace.service.workspace.flight;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.common.utils.FlightUtils;
import bio.terra.workspace.db.WorkspaceDao;
import bio.terra.workspace.service.workspace.WorkspaceCloudContext;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;

public class SetGoogleContextOutputStep implements Step {

  private final WorkspaceDao workspaceDao;

  @Autowired
  SetGoogleContextOutputStep(WorkspaceDao workspaceDao) {
    this.workspaceDao = workspaceDao;
  }

  @Override
  public StepResult doStep(FlightContext flightContext)
      throws InterruptedException, RetryException {
    UUID workspaceId =
        flightContext.getInputParameters().get(WorkspaceFlightMapKeys.WORKSPACE_ID, UUID.class);
    WorkspaceCloudContext cloudContext = workspaceDao.getCloudContext(workspaceId);

    FlightUtils.setResponse(flightContext, cloudContext, HttpStatus.OK);
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) throws InterruptedException {
    // Do not modify result - if this step is undoing, a different step has likely already set
    // the response to an exception which should be surfaced.
    return StepResult.getStepResultSuccess();
  }
}
