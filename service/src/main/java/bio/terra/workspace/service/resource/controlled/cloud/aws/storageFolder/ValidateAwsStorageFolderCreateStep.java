package bio.terra.workspace.service.resource.controlled.cloud.aws.storageFolder;

import bio.terra.cloudres.aws.bucket.S3BucketCow;
import bio.terra.common.exception.ConflictException;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.common.utils.AwsUtils;
import bio.terra.workspace.service.crl.CrlService;
import software.amazon.awssdk.awscore.exception.AwsServiceException;

public class ValidateAwsStorageFolderCreateStep implements Step {
  private final ControlledAwsStorageFolderResource resource;
  private final CrlService crlService;

  public ValidateAwsStorageFolderCreateStep(
      ControlledAwsStorageFolderResource resource, CrlService crlService) {
    this.resource = resource;
    this.crlService = crlService;
  }

  @Override
  public StepResult doStep(FlightContext flightContext)
      throws InterruptedException, RetryException {

    try (S3BucketCow s3BucketCow = crlService.createS3BucketCow(resource.getRegion())) {
      if (s3BucketCow.folderExists(resource.getBucketName(), resource.getPrefix())) {
        return new StepResult(
            StepStatus.STEP_RESULT_FAILURE_FATAL,
            new ConflictException(
                String.format(
                    "Prefix '%s/' already exists in bucket '%s'.",
                    resource.getPrefix(), resource.getBucketName())));
      }
      return StepResult.getStepResultSuccess();
    } catch (AwsServiceException ex) {
      return AwsUtils.handleAwsExceptionInFlight("Error while checking AWS storage folder: ", ex);
    }
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) throws InterruptedException {
    return StepResult.getStepResultSuccess();
  }
}
