package bio.terra.workspace.service.resource.controlled.cloud.aws.sageMakerNotebook;

import bio.terra.common.exception.ApiException;
import bio.terra.common.exception.NotFoundException;
import bio.terra.common.exception.UnauthorizedException;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.common.utils.AwsUtils;
import bio.terra.workspace.common.utils.FlightUtils;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.resource.controlled.flight.delete.DeleteControlledResourceStep;
import bio.terra.workspace.service.workspace.AwsCloudContextService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.services.sagemaker.model.NotebookInstanceStatus;

public class ValidateAwsSageMakerNotebookDeleteStep implements DeleteControlledResourceStep {
  private static final Logger logger =
      LoggerFactory.getLogger(ValidateAwsSageMakerNotebookDeleteStep.class);

  private final ControlledAwsSageMakerNotebookResource resource;
  private final AwsCloudContextService awsCloudContextService;
  private final SamService samService;

  public ValidateAwsSageMakerNotebookDeleteStep(
      ControlledAwsSageMakerNotebookResource resource,
      AwsCloudContextService awsCloudContextService,
      SamService samService) {
    this.resource = resource;
    this.awsCloudContextService = awsCloudContextService;
    this.samService = samService;
  }

  @Override
  public StepResult doStep(FlightContext flightContext)
      throws InterruptedException, RetryException {
    AwsCredentialsProvider credentialsProvider =
        AwsUtils.createWsmCredentialProvider(
            awsCloudContextService.getRequiredAuthentication(),
            awsCloudContextService.discoverEnvironment(
                FlightUtils.getRequiredUserEmail(flightContext.getInputParameters(), samService)));

    try {
      NotebookInstanceStatus notebookStatus =
          AwsUtils.getSageMakerNotebookStatus(credentialsProvider, resource);

      switch (notebookStatus) {
        case STOPPED, FAILED, DELETING -> {} // can delete
        default -> { // all other cases
          // TODO(TERRA-560) Store this as a Validation exception in Step result
          logger.error(
              String.format(
                  "Cannot stop AWS SageMaker Notebook resource %s, status %s.",
                  resource.getResourceId(), notebookStatus));
          return new StepResult(StepStatus.STEP_RESULT_FAILURE_FATAL);
        }
      }

    } catch (ApiException | UnauthorizedException e) {
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
