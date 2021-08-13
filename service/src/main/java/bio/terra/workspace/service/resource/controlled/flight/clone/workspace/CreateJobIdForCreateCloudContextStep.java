package bio.terra.workspace.service.resource.controlled.flight.clone.workspace;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import java.util.UUID;

public class CreateJobIdForCreateCloudContextStep implements Step {

  @Override
  public StepResult doStep(FlightContext context) throws InterruptedException, RetryException {
    final String jobId = UUID.randomUUID().toString();
    context.getWorkingMap().put(ControlledResourceKeys.CREATE_CLOUD_CONTEXT_JOB_ID, jobId);

    final UUID destinationWorkspaceId = UUID.randomUUID();
    context
        .getWorkingMap()
        .put(ControlledResourceKeys.DESTINATION_WORKSPACE_ID, destinationWorkspaceId);

    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    return StepResult.getStepResultSuccess();
  }
}
