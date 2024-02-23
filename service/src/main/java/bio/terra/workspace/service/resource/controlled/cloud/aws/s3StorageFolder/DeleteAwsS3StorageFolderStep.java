package bio.terra.workspace.service.resource.controlled.cloud.aws.s3StorageFolder;

import bio.terra.common.exception.ApiException;
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

public class DeleteAwsS3StorageFolderStep implements DeleteControlledResourceStep {

  private static final Logger logger = LoggerFactory.getLogger(DeleteAwsS3StorageFolderStep.class);
  private final ControlledAwsS3StorageFolderResource resource;
  private final AwsCloudContextService awsCloudContextService;
  private final SamService samService;

  public DeleteAwsS3StorageFolderStep(
      ControlledAwsS3StorageFolderResource resource,
      AwsCloudContextService awsCloudContextService,
      SamService samService) {
    this.resource = resource;
    this.awsCloudContextService = awsCloudContextService;
    this.samService = samService;
  }

  @VisibleForTesting
  static StepResult executeDeleteAwsS3StorageFolder(
      AwsCredentialsProvider credentialsProvider, ControlledAwsS3StorageFolderResource resource) {
    try {
      AwsUtils.deleteStorageFolder(credentialsProvider, resource);
    } catch (ApiException | UnauthorizedException e) {
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_FATAL, e);
    } catch (NotFoundException e) {
      logger.debug("No storage folder {} to delete.", resource.getName());
    }

    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult doStep(FlightContext flightContext)
      throws InterruptedException, RetryException {
    return executeDeleteAwsS3StorageFolder(
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
                "Cannot undo delete of AWS S3 Storage Folder resource %s in workspace %s.",
                resource.getResourceId(), resource.getWorkspaceId())));
  }
}
