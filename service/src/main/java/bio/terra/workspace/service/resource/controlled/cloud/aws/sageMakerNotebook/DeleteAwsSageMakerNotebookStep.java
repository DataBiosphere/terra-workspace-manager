package bio.terra.workspace.service.resource.controlled.cloud.aws.sageMakerNotebook;

import bio.terra.common.exception.ApiException;
import bio.terra.common.exception.BadRequestException;
import bio.terra.common.exception.NotFoundException;
import bio.terra.common.exception.UnauthorizedException;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.common.exception.InternalLogicException;
import bio.terra.workspace.common.utils.AwsUtils;
import bio.terra.workspace.common.utils.FlightUtils;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.resource.controlled.flight.delete.DeleteControlledResourceStep;
import bio.terra.workspace.service.workspace.AwsCloudContextService;
import com.google.common.annotations.VisibleForTesting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.services.sagemaker.model.NotebookInstanceStatus;

public class DeleteAwsSageMakerNotebookStep implements DeleteControlledResourceStep {

  private static final Logger logger =
      LoggerFactory.getLogger(DeleteAwsSageMakerNotebookStep.class);

  private final ControlledAwsSageMakerNotebookResource resource;
  private final AwsCloudContextService awsCloudContextService;
  private final SamService samService;

  public DeleteAwsSageMakerNotebookStep(
      ControlledAwsSageMakerNotebookResource resource,
      AwsCloudContextService awsCloudContextService,
      SamService samService) {
    this.resource = resource;
    this.awsCloudContextService = awsCloudContextService;
    this.samService = samService;
  }

  @VisibleForTesting
  static StepResult executeDeleteAwsSageMakerNotebook(
      AwsCredentialsProvider credentialsProvider, ControlledAwsSageMakerNotebookResource resource) {
    try {
      AwsUtils.deleteSageMakerNotebook(credentialsProvider, resource);
      AwsUtils.waitForSageMakerNotebookStatus(
          credentialsProvider, resource, NotebookInstanceStatus.DELETING);

    } catch (ApiException | UnauthorizedException | BadRequestException e) {
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_FATAL, e);

    } catch (NotFoundException e) {
      logger.debug("No notebook instance {} to delete.", resource.getName());
    }

    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult doStep(FlightContext flightContext)
      throws InterruptedException, RetryException {
    return executeDeleteAwsSageMakerNotebook(
        AwsUtils.createWsmCredentialProvider(
            awsCloudContextService.getRequiredAuthentication(),
            awsCloudContextService.discoverEnvironment(
                FlightUtils.getRequiredUserEmail(flightContext.getInputParameters(), samService))),
        resource);
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) throws InterruptedException {
    return new StepResult(
        flightContext.getResult().getStepStatus(),
        new InternalLogicException(
            String.format(
                "Cannot undo delete of AWS SageMaker Notebook resource %s in workspace %s.",
                resource.getResourceId(), resource.getWorkspaceId())));
  }
}
