package bio.terra.workspace.service.resource.controlled.cloud.aws.sagemakerNotebook;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.common.utils.AwsUtils;
import bio.terra.workspace.service.workspace.AwsCloudContextService;
import java.util.*;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.services.sagemaker.model.NotebookInstanceStatus;

public class WaitForAwsSagemakerNotebookStatusStep implements Step {
  private final ControlledAwsSagemakerNotebookResource resource;
  private final AwsCloudContextService awsCloudContextService;
  private final NotebookInstanceStatus notebookStatus;

  public WaitForAwsSagemakerNotebookStatusStep(
      ControlledAwsSagemakerNotebookResource resource,
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
