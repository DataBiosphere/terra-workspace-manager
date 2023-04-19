package bio.terra.workspace.service.resource.controlled.cloud.aws.storageFolder;

import bio.terra.cloudres.aws.bucket.S3BucketCow;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.common.utils.AwsUtils;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.resource.controlled.exception.AwsBucketNotFoundException;
import bio.terra.workspace.service.workspace.AwsCloudContextService;
import bio.terra.workspace.service.workspace.model.AwsCloudContext;
import com.google.api.client.http.HttpStatusCodes;
import java.util.Collection;
import java.util.HashSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.services.sts.model.Tag;

public class CreateAwsStorageFolderStep implements Step {
  private static final Logger logger = LoggerFactory.getLogger(CreateAwsStorageFolderStep.class);
  private final ControlledAwsStorageFolderResource resource;
  private final AwsCloudContextService awsCloudContextService;
  private final CrlService crlService;
  private final AuthenticatedUserRequest userRequest;

  public CreateAwsStorageFolderStep(
      ControlledAwsStorageFolderResource resource,
      AwsCloudContextService awsCloudContextService,
      CrlService crlService,
      AuthenticatedUserRequest userRequest) {
    this.resource = resource;
    this.awsCloudContextService = awsCloudContextService;
    this.crlService = crlService;
    this.userRequest = userRequest;
  }

  @Override
  public StepResult doStep(FlightContext flightContext)
      throws InterruptedException, RetryException {
    AwsCloudContext cloudContext =
        awsCloudContextService.getRequiredAwsCloudContext(resource.getWorkspaceId());

    Collection<Tag> stsTags = new HashSet<>();
    AwsUtils.appendUserTags(stsTags, userRequest);
    AwsUtils.appendResourceTags(stsTags, cloudContext);
    Collection<software.amazon.awssdk.services.s3.model.Tag> s3Tags = AwsUtils.convertTags(stsTags);

    try (S3BucketCow s3BucketCow = crlService.createS3BucketCow(resource.getRegion())) {
      s3BucketCow.createFolder(resource.getBucketName(), resource.getPrefix(), s3Tags);
    } catch (AwsServiceException ex) {
      if (ex.statusCode() == HttpStatusCodes.STATUS_CODE_FORBIDDEN
          || ex.statusCode() == HttpStatusCodes.STATUS_CODE_NOT_FOUND) {
        throw new AwsBucketNotFoundException(
            "AWS bucket not found while creating a folder. Something is wrong with support resources or discovery.",
            ex);
      } else {
        return AwsUtils.handleAwsExceptionInFlight("Error while creating AWS storage folder: ", ex);
      }
    }
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) throws InterruptedException {
    try (S3BucketCow s3BucketCow = crlService.createS3BucketCow(resource.getRegion())) {
      s3BucketCow.deleteFolder(resource.getBucketName(), resource.getPrefix());
    } catch (AwsServiceException ex) {
      // If the folder is not found, it may have been deleted already or never created.
      if (ex.statusCode() == HttpStatusCodes.STATUS_CODE_NOT_FOUND) {
        logger.info("S3 bucket {} already deleted", resource.getBucketName());
        return StepResult.getStepResultSuccess();
      } else {
        return AwsUtils.handleAwsExceptionInFlight(
            "Error while undoing AWS storage folder creation: ", ex);
      }
    }
    return StepResult.getStepResultSuccess();
  }
}
