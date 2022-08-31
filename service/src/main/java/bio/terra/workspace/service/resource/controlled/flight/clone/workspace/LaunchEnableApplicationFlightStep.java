package bio.terra.workspace.service.resource.controlled.flight.clone.workspace;

import static bio.terra.workspace.common.utils.FlightUtils.validateRequiredEntries;

import bio.terra.stairway.*;
import bio.terra.stairway.exception.DuplicateFlightIdException;
import bio.terra.stairway.exception.RetryException;
import bio.terra.stairway.exception.StairwayException;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.job.JobMapKeys;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.WsmApplicationKeys;
import bio.terra.workspace.service.workspace.flight.application.able.AbleEnum;
import bio.terra.workspace.service.workspace.flight.application.able.ApplicationAbleFlight;
import bio.terra.workspace.service.workspace.model.Workspace;
import java.util.List;

public class LaunchEnableApplicationFlightStep implements Step {
  @Override
  public StepResult doStep(FlightContext context) throws InterruptedException, RetryException {
    validateRequiredEntries(
        context.getInputParameters(),
        JobMapKeys.AUTH_USER_INFO.getKeyName(),
        JobMapKeys.REQUEST.getKeyName());
    validateRequiredEntries(
        context.getWorkingMap(),
        WorkspaceFlightMapKeys.APPLICATION_ID,
        WorkspaceFlightMapKeys.WsmApplicationKeys.APPLICATION_ABLE_ENUM);
    final var userRequest =
        context
            .getInputParameters()
            .get(JobMapKeys.AUTH_USER_INFO.getKeyName(), AuthenticatedUserRequest.class);
    final List<String> applicationIdList =
        context.getWorkingMap().get(WorkspaceFlightMapKeys.APPLICATION_ID, List.class);
    final AbleEnum ableEnum =
        context.getWorkingMap().get(WsmApplicationKeys.APPLICATION_ABLE_ENUM, AbleEnum.class);

    final var destinationWorkspace =
        context.getInputParameters().get(JobMapKeys.REQUEST.getKeyName(), Workspace.class);

    final Stairway stairway = context.getStairway();

    final FlightMap subflightInputParameters = new FlightMap();
    subflightInputParameters.put(JobMapKeys.AUTH_USER_INFO.getKeyName(), userRequest);
    subflightInputParameters.put(WorkspaceFlightMapKeys.APPLICATION_ID, applicationIdList);
    subflightInputParameters.put(WsmApplicationKeys.APPLICATION_ABLE_ENUM, ableEnum);

    // fields normally set by JobBuilder for identifying jobs
    subflightInputParameters.put(
        WorkspaceFlightMapKeys.WORKSPACE_ID, destinationWorkspace.getWorkspaceId().toString());
    subflightInputParameters.put(
        JobMapKeys.DESCRIPTION.getKeyName(),
        String.format(
            "Enable applications %s in workspace %s",
            applicationIdList, destinationWorkspace.getWorkspaceId()));

    // Build a ApplicationAbleFlight
    try {
      stairway.submit(
          context.getStairway().createFlightId(),
          ApplicationAbleFlight.class,
          subflightInputParameters);
    } catch (DuplicateFlightIdException e) {
      // exit early if flight is already going
      return StepResult.getStepResultSuccess();
    } catch (StairwayException e) {
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY, e);
    }
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    return StepResult.getStepResultSuccess();
  }
}
