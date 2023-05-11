package bio.terra.workspace.service.resource.controlled.cloud.aws.sageMakerNotebook;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.common.utils.AwsUtils;
import bio.terra.workspace.service.workspace.AwsCloudContextService;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.services.sagemaker.model.NotebookInstanceStatus;

public class WaitForAwsSageMakerNotebookStatusStep implements Step {
  private final ControlledAwsSageMakerNotebookResource resource;
  private final AwsCloudContextService awsCloudContextService;
  private final NotebookInstanceStatus notebookStatus;

  public WaitForAwsSageMakerNotebookStatusStep(
      ControlledAwsSageMakerNotebookResource resource,
      AwsCloudContextService awsCloudContextService,
      NotebookInstanceStatus notebookStatus) {
    this.resource = resource;
    this.awsCloudContextService = awsCloudContextService;
    this.notebookStatus = notebookStatus;
  }

  @Override
  public StepResult doStep(FlightContext flightContext)
      throws InterruptedException, RetryException {
    AwsCredentialsProvider credentialsProvider =
        AwsUtils.createWsmCredentialProvider(
            awsCloudContextService.getRequiredAuthentication(),
            awsCloudContextService.discoverEnvironment());

    AwsUtils.waitForSageMakerNotebookStatus(credentialsProvider, resource, notebookStatus);
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    return StepResult.getStepResultSuccess();
  }
}
