package bio.terra.workspace.service.resource.controlled.cloud.aws.storageFolder;

import bio.terra.cloudres.aws.bucket.S3BucketCow;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.common.exception.InternalLogicException;
import bio.terra.workspace.common.utils.AwsUtils;
import bio.terra.workspace.service.crl.CrlService;
import com.google.api.client.http.HttpStatusCodes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.awscore.exception.AwsServiceException;

public class DeleteAwsStorageFolderStep implements Step {
  private static final Logger logger = LoggerFactory.getLogger(DeleteAwsStorageFolderStep.class);
  private final ControlledAwsStorageFolderResource resource;
  private final CrlService crlService;

  public DeleteAwsStorageFolderStep(
      ControlledAwsStorageFolderResource resource, CrlService crlService) {
    this.resource = resource;
    this.crlService = crlService;
  }

  @Override
  public StepResult doStep(FlightContext flightContext)
      throws InterruptedException, RetryException {
    try (S3BucketCow s3BucketCow = crlService.createS3BucketCow(resource.getRegion())) {
      s3BucketCow.deleteFolder(resource.getBucketName(), resource.getPrefix());
    } catch (AwsServiceException ex) {
      // If the folder is not found, this step may already have deleted it.
      if (ex.statusCode() == HttpStatusCodes.STATUS_CODE_NOT_FOUND) {
        logger.info("S3 bucket {} already deleted", resource.getBucketName());
        return StepResult.getStepResultSuccess();
      } else {
        return AwsUtils.handleAwsExceptionInFlight("Error while deleting AWS storage folder: ", ex);
      }
    }
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) throws InterruptedException {
    return new StepResult(
        StepStatus.STEP_RESULT_FAILURE_FATAL,
        new InternalLogicException(
            String.format(
                "Cannot undo delete of AWS Storage Folder resource %s in workspace %s.",
                resource.getResourceId(), resource.getWorkspaceId())));
  }
}
