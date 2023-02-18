package bio.terra.workspace.service.workspace.flight.aws;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.workspace.service.workspace.AwsCloudContextService;
import java.util.UUID;

public class CreateDbAwsCloudContextStartStep implements Step {
  private final UUID workspaceUuid;
  private final AwsCloudContextService awsCloudContextService;

  public CreateDbAwsCloudContextStartStep(
      UUID workspaceUuid, AwsCloudContextService awsCloudContextService) {
    this.workspaceUuid = workspaceUuid;
    this.awsCloudContextService = awsCloudContextService;
  }

  @Override
  public StepResult doStep(FlightContext flightContext) throws InterruptedException {
    // TODO-Dex
    /*AwsCloudContext awsCloudContext = awsCloudContextService.fromConfiguration();

    if (awsCloudContext == null) {
        return new StepResult(
                StepStatus.STEP_RESULT_FAILURE_FATAL,
                new AwsLandingZoneException("No default AWS Landing Zone configured"));
    }

    String serializedAwsCloudContext = awsCloudContext.serialize();

    // Create the AWS Cloud Context from the current configuration and put it into the working map
    flightContext.getWorkingMap().put(AWS_CLOUD_CONTEXT, serializedAwsCloudContext);

    awsCloudContextService.createAwsCloudContextStart(workspaceUuid, flightContext.getFlightId());
    return StepResult.getStepResultSuccess();

     */
    return null;
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) throws InterruptedException {
    // Delete the cloud context, but only if it is the one we created
    awsCloudContextService.deleteAwsCloudContextWithFlightIdValidation(
        workspaceUuid, flightContext.getFlightId());
    return StepResult.getStepResultSuccess();
  }
}
