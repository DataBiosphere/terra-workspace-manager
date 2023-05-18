package bio.terra.workspace.service.workspace.flight.create.workspace;

import static java.lang.Boolean.TRUE;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.common.utils.FlightUtils;
import bio.terra.workspace.db.WorkspaceDao;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ResourceKeys;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;

/** Finish the workspace creation */
public class CreateWorkspaceFinishStep implements Step {
  private static final Logger logger = LoggerFactory.getLogger(CreateWorkspaceFinishStep.class);

  private final WorkspaceDao workspaceDao;
  private final UUID workspaceUuid;

  public CreateWorkspaceFinishStep(UUID workspaceUuid, WorkspaceDao workspaceDao) {
    this.workspaceDao = workspaceDao;
    this.workspaceUuid = workspaceUuid;
  }

  @Override
  public StepResult doStep(FlightContext flightContext)
      throws InterruptedException, RetryException {
    FlightMap workingMap = flightContext.getWorkingMap();

    workingMap.put(ResourceKeys.UPDATE_COMPLETE, Boolean.FALSE);
    workspaceDao.createWorkspaceSuccess(workspaceUuid, flightContext.getFlightId());
    workingMap.put(ResourceKeys.UPDATE_COMPLETE, TRUE);

    FlightUtils.setResponse(flightContext, workspaceUuid, HttpStatus.OK);
    logger.info("Workspace created with id {}", workspaceUuid);

    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) throws InterruptedException {
    Boolean didUpdate =
        flightContext.getWorkingMap().get(ResourceKeys.UPDATE_COMPLETE, Boolean.class);
    if (TRUE.equals(didUpdate)) {
      // If the update is complete, then we cannot undo it. This is a teeny tiny window
      // where the error occurs after the update, but before the success return. However,
      // the DebugInfo.lastStepFailure will always hit it.
      return new StepResult(
          StepStatus.STEP_RESULT_FAILURE_FATAL, new RuntimeException("dismal failure"));
    }
    // Failed before update - perform undo
    return StepResult.getStepResultSuccess();
  }
}
