package bio.terra.workspace.service.resource.controlled.flight.create;

import static bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys.AWS_CLOUD_CONTEXT;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.service.workspace.AwsCloudContextService;
import bio.terra.workspace.service.workspace.model.AwsCloudContext;
import java.util.UUID;

/**
 * Retrieve AWS the cloud context, if applicable, and store it in the working map. Since this step
 * only reads data, it is idempotent
 */
public class GetAwsCloudContextStep implements Step {

  private final UUID workspaceUuid;
  private final AwsCloudContextService awsCloudContextService;

  public GetAwsCloudContextStep(UUID workspaceUuid, AwsCloudContextService awsCloudContextService) {
    this.workspaceUuid = workspaceUuid;
    this.awsCloudContextService = awsCloudContextService;
  }

  @Override
  public StepResult doStep(FlightContext flightContext)
      throws InterruptedException, RetryException {
    FlightMap workingMap = flightContext.getWorkingMap();
    if (workingMap.get(AWS_CLOUD_CONTEXT, AwsCloudContext.class) == null) {
      workingMap.put(
          AWS_CLOUD_CONTEXT, awsCloudContextService.getRequiredAwsCloudContext(workspaceUuid));
    }

    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) throws InterruptedException {
    return StepResult.getStepResultSuccess();
  }
}
