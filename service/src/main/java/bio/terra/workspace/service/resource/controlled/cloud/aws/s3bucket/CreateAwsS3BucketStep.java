package bio.terra.workspace.service.resource.controlled.cloud.aws.s3bucket;

import static bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys.AWS_CLOUD_CONTEXT;

import bio.terra.common.iam.SamUser;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.common.utils.AwsUtils;
import bio.terra.workspace.common.utils.MultiCloudUtils;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import bio.terra.workspace.service.workspace.model.AwsCloudContext;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sts.model.Credentials;

public class CreateAwsS3BucketStep implements Step {
  private final ControlledAwsS3BucketResource resource;

  public CreateAwsS3BucketStep(ControlledAwsS3BucketResource resource) {
    this.resource = resource;
  }

  @Override
  public StepResult doStep(FlightContext flightContext)
      throws InterruptedException, RetryException {
    FlightMap inputParameters = flightContext.getInputParameters();

    AwsCloudContext awsCloudContext =
        flightContext.getWorkingMap().get(AWS_CLOUD_CONTEXT, AwsCloudContext.class);
    Credentials awsCredentials = MultiCloudUtils.assumeAwsServiceRoleFromGcp(awsCloudContext);
    SamUser samUser = inputParameters.get(WorkspaceFlightMapKeys.SAM_USER, SamUser.class);

    AwsUtils.createFolder(
        awsCredentials,
        resource.getWorkspaceId(),
        samUser,
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

    AwsUtils.deleteFolder(
        awsCredentials,
        Region.of(resource.getRegion()),
        resource.getS3BucketName(),
        resource.getPrefix());
    return StepResult.getStepResultSuccess();
  }
}
