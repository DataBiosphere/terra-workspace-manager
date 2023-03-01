package bio.terra.workspace.service.resource.controlled.cloud.aws.storagebucket;

import static bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys.AWS_CLOUD_CONTEXT;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.common.utils.AwsUtils;
import bio.terra.workspace.common.utils.MultiCloudUtils;
import bio.terra.workspace.service.workspace.model.AwsCloudContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sts.model.Credentials;

public class CreateAwsBucketStep implements Step {
  private static final Logger logger = LoggerFactory.getLogger(CreateAwsBucketStep.class);

  private final ControlledAwsBucketResource resource;

  public CreateAwsBucketStep(ControlledAwsBucketResource resource) {
    this.resource = resource;
  }

  @Override
  public StepResult doStep(FlightContext flightContext)
      throws InterruptedException, RetryException {
    AwsCloudContext awsCloudContext =
        flightContext.getWorkingMap().get(AWS_CLOUD_CONTEXT, AwsCloudContext.class);
    Credentials awsCredentials = MultiCloudUtils.assumeAwsServiceRoleFromGcp(awsCloudContext);

    AwsUtils.createFolder(
        awsCredentials,
        Region.of(resource.getRegion()),
        resource.getS3BucketName(),
        resource.getPrefix());
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) throws InterruptedException {
    AwsCloudContext awsCloudContext =
        flightContext.getWorkingMap().get(AWS_CLOUD_CONTEXT, AwsCloudContext.class);
    Credentials awsCredentials = MultiCloudUtils.assumeAwsServiceRoleFromGcp(awsCloudContext);

    AwsUtils.undoCreateFolder(
        awsCredentials,
        Region.of(resource.getRegion()),
        resource.getS3BucketName(),
        resource.getPrefix());
    return StepResult.getStepResultSuccess();
  }
}
