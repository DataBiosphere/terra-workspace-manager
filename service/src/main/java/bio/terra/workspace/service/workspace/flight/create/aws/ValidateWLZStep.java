package bio.terra.workspace.service.workspace.flight.create.aws;

import static bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys.AWS_CLOUD_CONTEXT;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.workspace.service.workspace.model.AwsCloudContext;

public class ValidateWLZStep implements Step {

  @Override
  public StepResult doStep(FlightContext flightContext) throws InterruptedException {

    String serializedAwsCloudContext =
        flightContext.getWorkingMap().get(AWS_CLOUD_CONTEXT, String.class);

    AwsCloudContext awsCloudContext = AwsCloudContext.deserialize(serializedAwsCloudContext);

    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) throws InterruptedException {
    return StepResult.getStepResultSuccess();
  }
}
