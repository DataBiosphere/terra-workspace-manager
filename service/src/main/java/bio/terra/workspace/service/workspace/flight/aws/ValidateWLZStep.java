package bio.terra.workspace.service.workspace.flight.aws;

import static bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys.AWS_CLOUD_CONTEXT;

import bio.terra.common.iam.SamUser;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.workspace.common.utils.MultiCloudUtils;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import bio.terra.workspace.service.workspace.model.AwsCloudContext;
import java.util.HashSet;

public class ValidateWLZStep implements Step {

  @Override
  public StepResult doStep(FlightContext flightContext) throws InterruptedException {
    try {
      AwsCloudContext awsCloudContext =
          flightContext.getWorkingMap().get(AWS_CLOUD_CONTEXT, AwsCloudContext.class);
      SamUser samUser =
          flightContext.getInputParameters().get(WorkspaceFlightMapKeys.SAM_USER, SamUser.class);

      MultiCloudUtils.assumeAwsUserRoleFromGcp(awsCloudContext, samUser, new HashSet<>());

    } catch (Exception e) {
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_FATAL, e);
    }

    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) throws InterruptedException {
    return StepResult.getStepResultSuccess();
  }
}
