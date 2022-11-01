package bio.terra.workspace.service.workspace.flight.create.aws;

import static bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys.AWS_CLOUD_CONTEXT;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.workspace.service.workspace.AwsCloudContextService;
import bio.terra.workspace.service.workspace.exceptions.AwsLandingZoneException;
import bio.terra.workspace.service.workspace.model.AwsCloudContext;
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

    AwsCloudContext awsCloudContext = awsCloudContextService.fromConfiguration();

    if (awsCloudContext == null) {
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_FATAL, new AwsLandingZoneException("No default AWS Landing Zone configured"));
    }

    String serializedAwsCloudContext = awsCloudContext.serialize();

    // Create the AWS Cloud Context from the current configuration and put it into the working map
    flightContext.getWorkingMap().put(AWS_CLOUD_CONTEXT, serializedAwsCloudContext);

    awsCloudContextService.createAwsCloudContextStart(workspaceUuid, flightContext.getFlightId());
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) throws InterruptedException {
    // Delete the cloud context, but only if it is the one we created
    awsCloudContextService.deleteAwsCloudContextWithFlightIdValidation(
        workspaceUuid, flightContext.getFlightId());
    return StepResult.getStepResultSuccess();
  }
}
