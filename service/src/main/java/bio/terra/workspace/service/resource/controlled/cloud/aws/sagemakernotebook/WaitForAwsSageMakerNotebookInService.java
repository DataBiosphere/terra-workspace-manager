package bio.terra.workspace.service.resource.controlled.cloud.aws.sagemakernotebook;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.common.utils.AwsUtils;
import bio.terra.workspace.common.utils.MultiCloudUtils;
import bio.terra.workspace.generated.model.ApiAwsSageMakerNotebookCreationParameters;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import bio.terra.workspace.service.workspace.model.AwsCloudContext;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.securitytoken.model.Credentials;

public class WaitForAwsSageMakerNotebookInService implements Step {
  @Override
  public StepResult doStep(FlightContext flightContext)
      throws InterruptedException, RetryException {
    FlightMap inputParameters = flightContext.getInputParameters();
    FlightMap workingMap = flightContext.getWorkingMap();

    final ApiAwsSageMakerNotebookCreationParameters creationParameters =
        inputParameters.get(
            WorkspaceFlightMapKeys.ControlledResourceKeys.CREATE_NOTEBOOK_PARAMETERS,
            ApiAwsSageMakerNotebookCreationParameters.class);

    final String awsCloudContextString =
        workingMap.get(
            WorkspaceFlightMapKeys.ControlledResourceKeys.AWS_CLOUD_CONTEXT, String.class);

    final AwsCloudContext awsCloudContext = AwsCloudContext.deserialize(awsCloudContextString);
    final Credentials awsCredentials = MultiCloudUtils.assumeAwsServiceRoleFromGcp(awsCloudContext);

    Regions region = Regions.fromName(creationParameters.getLocation());

    AwsUtils.waitForSageMakerNotebookInService(
        awsCredentials, region, creationParameters.getInstanceId());

    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    return StepResult.getStepResultSuccess();
  }
}
