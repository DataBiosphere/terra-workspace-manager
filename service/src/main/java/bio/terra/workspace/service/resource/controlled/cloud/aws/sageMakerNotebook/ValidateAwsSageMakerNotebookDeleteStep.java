package bio.terra.workspace.service.resource.controlled.cloud.aws.sageMakerNotebook;

import static bio.terra.workspace.common.utils.AwsUtils.notebookStatusSetCanDelete;

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

public class ValidateAwsSageMakerNotebookDeleteStep implements Step {

  private static final Logger logger =
      LoggerFactory.getLogger(ValidateAwsSageMakerNotebookDeleteStep.class);

  private final ControlledAwsSageMakerNotebookResource resource;
  private final AwsCloudContextService awsCloudContextService;

  public ValidateAwsSageMakerNotebookDeleteStep(
      ControlledAwsSageMakerNotebookResource resource,
      AwsCloudContextService awsCloudContextService) {
    this.resource = resource;
    this.awsCloudContextService = awsCloudContextService;
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

      if (!notebookStatusSetCanDelete.contains(notebookStatus)
          && (notebookStatus != NotebookInstanceStatus.DELETING)) {
        // TODO(TERRA-560) Store this as a Validation exception in Step result
        logger.error(
            "Expected notebook instance {} status in {}, but actual status is {}",
            resource.getResourceId(),
            notebookStatusSetCanDelete,
            notebookStatus);
        return new StepResult(StepStatus.STEP_RESULT_FAILURE_FATAL);
      }

    } catch (ApiException e) {
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_FATAL, e);

    } catch (NotFoundException e) {
      logger.debug("No notebook instance {} found.", resource.getResourceId());
    }

    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) throws InterruptedException {
    return StepResult.getStepResultSuccess();
  }
}
