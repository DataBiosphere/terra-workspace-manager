package bio.terra.workspace.service.resource.controlled.cloud.aws.sagemakernotebook;

import static bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys.AWS_CLOUD_CONTEXT;
import static bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys.CREATE_NOTEBOOK_PARAMETERS;

import bio.terra.common.exception.ApiException;
import bio.terra.common.exception.NotFoundException;
import bio.terra.common.iam.SamUser;
import bio.terra.stairway.*;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.common.utils.AwsUtils;
import bio.terra.workspace.common.utils.MultiCloudUtils;
import bio.terra.workspace.generated.model.ApiAwsSageMakerNotebookCreationParameters;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import bio.terra.workspace.service.workspace.model.AwsCloudContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sagemaker.model.InstanceType;
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

    ApiAwsSageMakerNotebookCreationParameters creationParameters =
        inputParameters.get(
            CREATE_NOTEBOOK_PARAMETERS, ApiAwsSageMakerNotebookCreationParameters.class);

    AwsCloudContext awsCloudContext =
        flightContext.getWorkingMap().get(AWS_CLOUD_CONTEXT, AwsCloudContext.class);
    Credentials awsCredentials = MultiCloudUtils.assumeAwsServiceRoleFromGcp(awsCloudContext);
    SamUser samUser = inputParameters.get(WorkspaceFlightMapKeys.SAM_USER, SamUser.class);

    AwsUtils.createSageMakerNotebook(
        awsCloudContext,
        awsCredentials,
        resource.getWorkspaceId(),
        samUser,
        Region.of(creationParameters.getLocation()),
        InstanceType.fromValue(creationParameters.getInstanceType()),
        creationParameters.getInstanceId(),
        false);
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) throws InterruptedException {
    AwsCloudContext awsCloudContext =
        flightContext.getWorkingMap().get(AWS_CLOUD_CONTEXT, AwsCloudContext.class);
    Credentials awsCredentials = MultiCloudUtils.assumeAwsServiceRoleFromGcp(awsCloudContext);

    Region region = Region.of(resource.getRegion());
    String notebookName = resource.getInstanceId();

    try {
      AwsUtils.stopSageMakerNotebook(awsCredentials, region, notebookName, true);
      AwsUtils.deleteSageMakerNotebook(awsCredentials, region, notebookName, true);

    } catch (ApiException e) {
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_FATAL, e);
    } catch (NotFoundException e) {
      logger.debug("No notebook instance {} to delete.", notebookName);
      return StepResult.getStepResultSuccess();
    }

    return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY);
  }
}
