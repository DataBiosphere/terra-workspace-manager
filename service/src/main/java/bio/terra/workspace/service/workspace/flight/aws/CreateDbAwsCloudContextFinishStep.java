package bio.terra.workspace.service.workspace.flight.aws;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.workspace.service.workspace.AwsCloudContextService;
import java.util.UUID;

public class CreateDbAwsCloudContextFinishStep implements Step {
  private final UUID workspaceUuid;
  private final AwsCloudContextService awsCloudContextService;

  public CreateDbAwsCloudContextFinishStep(
      UUID workspaceUuid, AwsCloudContextService awsCloudContextService) {
    this.workspaceUuid = workspaceUuid;
    this.awsCloudContextService = awsCloudContextService;
  }

  @Override
  public StepResult doStep(FlightContext flightContext) throws InterruptedException {
    // // TODO-Dex
    /*
    String serializedAwsCloudContext =
            flightContext.getWorkingMap().get(AWS_CLOUD_CONTEXT, String.class);

    AwsCloudContext awsCloudContext = AwsCloudContext.deserialize(serializedAwsCloudContext);

    // Create the cloud context; throws if the context already exists.
    awsCloudContextService.createAwsCloudContextFinish(
            workspaceUuid, awsCloudContext, flightContext.getFlightId());

    CloudContextHolder cch = new CloudContextHolder();
    cch.setAwsCloudContext(awsCloudContext.serialize());

    FlightUtils.setResponse(flightContext, cch, HttpStatus.OK);
    return StepResult.getStepResultSuccess();
     */
    return null;
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) throws InterruptedException {
    // We do not undo anything here. The create step will delete the row, if need be.
    return StepResult.getStepResultSuccess();
  }
}
