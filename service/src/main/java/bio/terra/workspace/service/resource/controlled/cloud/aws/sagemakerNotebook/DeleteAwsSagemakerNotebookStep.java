package bio.terra.workspace.service.resource.controlled.cloud.aws.sagemakerNotebook;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.common.exception.InternalLogicException;
import bio.terra.workspace.common.utils.AwsUtils;
import bio.terra.workspace.service.workspace.AwsCloudContextService;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;

public class DeleteAwsSagemakerNotebookStep implements Step {
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

    // TODO-Dex

    /*AwsUtils.createFolder(
       credentialsProvider,
       Region.of(resource.getRegion()),
       resource.getBucketName(),
       resource.getPrefix(),
       tags);
    */
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) throws InterruptedException {
    return new StepResult(
        StepStatus.STEP_RESULT_FAILURE_FATAL,
        new InternalLogicException(
            String.format(
                "Cannot undo delete of AWS Sagemaker Notebook resource %s in workspace %s.",
                resource.getResourceId(), resource.getWorkspaceId())));
  }
}
