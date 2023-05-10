package bio.terra.workspace.service.resource.controlled.cloud.aws.s3StorageFolder;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.common.exception.InternalLogicException;
import bio.terra.workspace.common.utils.AwsUtils;
import bio.terra.workspace.service.workspace.AwsCloudContextService;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;

public class DeleteAwsS3StorageFolderStep implements Step {
  private final ControlledAwsS3StorageFolderResource resource;
  private final AwsCloudContextService awsCloudContextService;

  public DeleteAwsS3StorageFolderStep(
      ControlledAwsS3StorageFolderResource resource,
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

    AwsUtils.deleteStorageFolder(credentialsProvider, resource);
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) throws InterruptedException {
    return new StepResult(
        flightContext.getResult().getStepStatus(),
        new InternalLogicException(
            String.format(
                "Cannot undo delete of AWS S3 Storage Folder resource %s in workspace %s.",
                resource.getResourceId(), resource.getWorkspaceId())));
  }
}
