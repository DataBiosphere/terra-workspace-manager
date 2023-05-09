package bio.terra.workspace.service.resource.controlled.cloud.aws.sagemakerNotebook;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.common.exception.InternalLogicException;
import bio.terra.workspace.common.utils.AwsUtils;
import bio.terra.workspace.service.workspace.AwsCloudContextService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;

public class DeleteAwsSagemakerNotebookStep implements Step {

  private static final Logger logger =
      LoggerFactory.getLogger(DeleteAwsSagemakerNotebookStep.class);

  private final ControlledAwsSagemakerNotebookResource resource;
  private final AwsCloudContextService awsCloudContextService;

  public DeleteAwsSagemakerNotebookStep(
      ControlledAwsSagemakerNotebookResource resource,
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

    AwsUtils.deleteSageMakerNotebook(credentialsProvider, resource);

    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) throws InterruptedException {
    return new StepResult(
        StepStatus.STEP_RESULT_FAILURE_FATAL,
        new InternalLogicException(
            String.format(
                "Cannot undo delete of AWS SageMaker Notebook resource %s in workspace %s.",
                resource.getResourceId(), resource.getWorkspaceId())));
  }
}
