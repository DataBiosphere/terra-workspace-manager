package bio.terra.workspace.service.resource.controlled.cloud.aws.s3bucket;

import static bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys.AWS_CLOUD_CONTEXT;

import bio.terra.common.exception.ConflictException;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.common.utils.AwsUtils;
import bio.terra.workspace.common.utils.MultiCloudUtils;
import bio.terra.workspace.service.workspace.model.AwsCloudContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sts.model.Credentials;

class ValidateAwsS3BucketCreationStep implements Step {
  private static final Logger logger =
      LoggerFactory.getLogger(ValidateAwsS3BucketCreationStep.class);

  private final ControlledAwsS3BucketResource resource;

  ValidateAwsS3BucketCreationStep(ControlledAwsS3BucketResource resource) {
    this.resource = resource;
  }

  @Override
  public StepResult doStep(FlightContext flightContext)
      throws InterruptedException, RetryException {
    AwsCloudContext awsCloudContext =
        flightContext.getWorkingMap().get(AWS_CLOUD_CONTEXT, AwsCloudContext.class);
    Credentials awsCredentials = MultiCloudUtils.assumeAwsServiceRoleFromGcp(awsCloudContext);

    if (AwsUtils.checkFolderExistence(
        awsCredentials,
        Region.of(resource.getRegion()),
        resource.getS3BucketName(),
        resource.getPrefix())) {
      return new StepResult(
          StepStatus.STEP_RESULT_FAILURE_FATAL,
          new ConflictException(
              String.format(
                  "Prefix '%s/' already exists in bucket '%s'.",
                  resource.getS3BucketName(), resource.getPrefix())));
    }
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    return StepResult.getStepResultSuccess();
  }
}
