package bio.terra.workspace.service.resource.controlled.cloud.aws.storagebucket;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.common.utils.AwsUtils;
import bio.terra.workspace.common.utils.MultiCloudUtils;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import bio.terra.workspace.service.workspace.model.AwsCloudContext;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.securitytoken.model.Credentials;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CreateAwsBucketStep implements Step {
  private static final Logger logger = LoggerFactory.getLogger(CreateAwsBucketStep.class);

  private final ControlledAwsBucketResource resource;

  public CreateAwsBucketStep(ControlledAwsBucketResource resource) {
    this.resource = resource;
  }

  @Override
  public StepResult doStep(FlightContext flightContext)
      throws InterruptedException, RetryException {
    final String awsCloudContextString =
        flightContext
            .getWorkingMap()
            .get(WorkspaceFlightMapKeys.ControlledResourceKeys.AWS_CLOUD_CONTEXT, String.class);

    final AwsCloudContext awsCloudContext = AwsCloudContext.deserialize(awsCloudContextString);
    final Credentials awsCredentials = MultiCloudUtils.assumeAwsServiceRoleFromGcp(awsCloudContext);

    AwsUtils.createFolder(
        awsCredentials,
        Regions.fromName(resource.getRegion()),
        resource.getS3BucketName(),
        resource.getPrefix());
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) throws InterruptedException {
    final String awsCloudContextString =
        flightContext
            .getWorkingMap()
            .get(WorkspaceFlightMapKeys.ControlledResourceKeys.AWS_CLOUD_CONTEXT, String.class);

    final AwsCloudContext awsCloudContext = AwsCloudContext.deserialize(awsCloudContextString);
    final Credentials awsCredentials = MultiCloudUtils.assumeAwsServiceRoleFromGcp(awsCloudContext);

    AwsUtils.undoCreateFolder(
        awsCredentials,
        Regions.fromName(resource.getRegion()),
        resource.getS3BucketName(),
        resource.getPrefix());
    return StepResult.getStepResultSuccess();
  }
}
