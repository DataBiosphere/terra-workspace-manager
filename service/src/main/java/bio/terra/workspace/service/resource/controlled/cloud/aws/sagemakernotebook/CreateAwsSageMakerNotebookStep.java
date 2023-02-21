package bio.terra.workspace.service.resource.controlled.cloud.aws.sagemakernotebook;

import bio.terra.common.exception.ApiException;
import bio.terra.common.iam.SamUser;
import bio.terra.stairway.*;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.common.utils.AwsUtils;
import bio.terra.workspace.common.utils.MultiCloudUtils;
import bio.terra.workspace.generated.model.ApiAwsSageMakerNotebookCreationParameters;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import bio.terra.workspace.service.workspace.model.AwsCloudContext;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sagemaker.model.InstanceType;
import software.amazon.awssdk.services.sagemaker.model.NotebookInstanceStatus;
import software.amazon.awssdk.services.sts.model.Credentials;

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
    Region region = Region.of(creationParameters.getLocation());

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
  public StepResult undoStep(FlightContext flightContext) throws InterruptedException {
    FlightMap workingMap = flightContext.getWorkingMap();
    final String awsCloudContextString =
        workingMap.get(
            WorkspaceFlightMapKeys.ControlledResourceKeys.AWS_CLOUD_CONTEXT, String.class);

    final AwsCloudContext awsCloudContext = AwsCloudContext.deserialize(awsCloudContextString);
    final Credentials awsCredentials = MultiCloudUtils.assumeAwsServiceRoleFromGcp(awsCloudContext);

    Region region = Region.of(resource.getRegion());
    String notebookName = resource.getInstanceId();

    try {
      Optional<NotebookInstanceStatus> notebookStatus =
          AwsUtils.getSageMakerNotebookStatus(awsCredentials, region, notebookName);
      if (notebookStatus.isEmpty()) {
        logger.debug("No notebook instance {} to delete.", notebookName);
        return StepResult.getStepResultSuccess();
      }

      AwsUtils.stopSageMakerNotebook(awsCredentials, region, notebookName);

      AwsUtils.deleteSageMakerNotebook(awsCredentials, region, notebookName);

    } catch (ApiException e) {
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_FATAL, e);
    }

    return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY);
  }
}
