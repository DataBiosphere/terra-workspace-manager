package bio.terra.workspace.service.resource.controlled.cloud.aws.sagemakernotebook;

import bio.terra.common.iam.SamUser;
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
import com.amazonaws.services.sagemaker.model.InstanceType;
import com.amazonaws.services.securitytoken.model.Credentials;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CreateAwsSageMakerNotebookStep implements Step {
  private static final Logger logger =
      LoggerFactory.getLogger(CreateAwsSageMakerNotebookStep.class);

  private final ControlledAwsSageMakerNotebookResource resource;

  public CreateAwsSageMakerNotebookStep(ControlledAwsSageMakerNotebookResource resource) {
    this.resource = resource;
  }

  @Override
  public StepResult doStep(FlightContext flightContext)
      throws InterruptedException, RetryException {
    FlightMap inputParameters = flightContext.getInputParameters();
    FlightMap workingMap = flightContext.getWorkingMap();

    final ApiAwsSageMakerNotebookCreationParameters creationParameters =
            inputParameters.get(
            WorkspaceFlightMapKeys.ControlledResourceKeys.CREATE_NOTEBOOK_PARAMETERS,
            ApiAwsSageMakerNotebookCreationParameters.class);

    final SamUser samUser = inputParameters.get(WorkspaceFlightMapKeys.SAM_USER, SamUser.class);

    final String awsCloudContextString =
        workingMap.get(
            WorkspaceFlightMapKeys.ControlledResourceKeys.AWS_CLOUD_CONTEXT, String.class);

    final AwsCloudContext awsCloudContext = AwsCloudContext.deserialize(awsCloudContextString);
    final Credentials awsCredentials = MultiCloudUtils.assumeAwsServiceRoleFromGcp(awsCloudContext);

    InstanceType instanceType = InstanceType.fromValue(creationParameters.getInstanceType());
    Regions region = Regions.fromName(creationParameters.getLocation());

    AwsUtils.createSageMakerNotebook(
        awsCloudContext,
        awsCredentials,
        resource.getWorkspaceId(),
        samUser,
        region,
        instanceType,
        creationParameters.getInstanceId());

    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    return null;
  }
}
