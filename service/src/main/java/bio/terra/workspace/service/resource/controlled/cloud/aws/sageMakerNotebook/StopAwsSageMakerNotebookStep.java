package bio.terra.workspace.service.resource.controlled.cloud.aws.sageMakerNotebook;

import static bio.terra.workspace.common.utils.AwsUtils.notebookStatusSetCanStart;
import static bio.terra.workspace.common.utils.AwsUtils.notebookStatusSetCanStop;

import bio.terra.common.exception.ApiException;
import bio.terra.common.exception.NotFoundException;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.common.utils.AwsUtils;
import bio.terra.workspace.service.workspace.AwsCloudContextService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.services.sagemaker.model.NotebookInstanceStatus;

public class StopAwsSageMakerNotebookStep implements Step {

  private static final Logger logger = LoggerFactory.getLogger(StopAwsSageMakerNotebookStep.class);

  private final ControlledAwsSageMakerNotebookResource resource;
  private final AwsCloudContextService awsCloudContextService;
  private final boolean resourceDeletion;

  public StopAwsSageMakerNotebookStep(
      ControlledAwsSageMakerNotebookResource resource,
      AwsCloudContextService awsCloudContextService,
      boolean resourceDeletion) {
    this.resource = resource;
    this.awsCloudContextService = awsCloudContextService;
    this.resourceDeletion = resourceDeletion;
  }

  @Override
  public StepResult doStep(FlightContext flightContext)
      throws InterruptedException, RetryException {
    AwsCredentialsProvider credentialsProvider =
        AwsUtils.createWsmCredentialProvider(
            awsCloudContextService.getRequiredAuthentication(),
            awsCloudContextService.discoverEnvironment());

    try {
      NotebookInstanceStatus notebookStatus =
          AwsUtils.getSageMakerNotebookStatus(credentialsProvider, resource);

      if (notebookStatusSetCanStop.contains(notebookStatus)) {
        AwsUtils.stopSageMakerNotebook(credentialsProvider, resource);
        AwsUtils.waitForSageMakerNotebookStatus(
            credentialsProvider, resource, NotebookInstanceStatus.STOPPED);

      } else if (notebookStatus == NotebookInstanceStatus.STOPPING) {
        AwsUtils.waitForSageMakerNotebookStatus(
            credentialsProvider, resource, NotebookInstanceStatus.STOPPED);

      } else if (notebookStatus != NotebookInstanceStatus.STOPPED) {
        if (!resourceDeletion) {
          throw new ApiException(
              String.format(
                  "Cannot stop AWS SageMaker Notebook resource %s, status %s.",
                  resource.getResourceId(), notebookStatus));
        }
        // else: already deleted
      }
      // else: already stopped

    } catch (ApiException e) {
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_FATAL, e);

    } catch (NotFoundException e) {
      if (!resourceDeletion) {
        return new StepResult(StepStatus.STEP_RESULT_FAILURE_FATAL, e);
      }
      logger.debug("No notebook instance {} found.", resource.getResourceId());
    }

    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) throws InterruptedException {
    AwsCredentialsProvider credentialsProvider =
        AwsUtils.createWsmCredentialProvider(
            awsCloudContextService.getRequiredAuthentication(),
            awsCloudContextService.discoverEnvironment());

    try {
      NotebookInstanceStatus notebookStatus =
          AwsUtils.getSageMakerNotebookStatus(credentialsProvider, resource);

      if (notebookStatusSetCanStart.contains(notebookStatus)) {
        AwsUtils.stopSageMakerNotebook(credentialsProvider, resource);
        AwsUtils.waitForSageMakerNotebookStatus(
            credentialsProvider, resource, NotebookInstanceStatus.STOPPED);

      } else if (notebookStatus == NotebookInstanceStatus.PENDING
          || notebookStatus == NotebookInstanceStatus.UPDATING) {
        AwsUtils.waitForSageMakerNotebookStatus(
            credentialsProvider, resource, NotebookInstanceStatus.IN_SERVICE);

      } else if (notebookStatus != NotebookInstanceStatus.IN_SERVICE) {
        throw new ApiException(
            String.format(
                "Cannot start AWS SageMaker Notebook resource %s, status %s.",
                resource.getResourceId(), notebookStatus));
      }
      // else: already started

    } catch (ApiException | NotFoundException e) {
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_FATAL, e);
    }

    return StepResult.getStepResultSuccess();
  }
}
