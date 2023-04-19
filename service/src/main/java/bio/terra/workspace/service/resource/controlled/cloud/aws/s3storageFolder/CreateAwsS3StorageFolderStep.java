package bio.terra.workspace.service.resource.controlled.cloud.aws.s3storageFolder;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.common.utils.AwsUtils;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.workspace.AwsCloudContextService;
import bio.terra.workspace.service.workspace.model.AwsCloudContext;
import java.util.Collection;
import java.util.HashSet;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sts.model.Tag;

public class CreateAwsS3StorageFolderStep implements Step {
  private final ControlledAwsS3StorageFolderResource resource;
  private final AwsCloudContextService awsCloudContextService;
  private final AuthenticatedUserRequest userRequest;

  public CreateAwsS3StorageFolderStep(
      ControlledAwsS3StorageFolderResource resource,
      AwsCloudContextService awsCloudContextService,
      AuthenticatedUserRequest userRequest) {
    this.resource = resource;
    this.awsCloudContextService = awsCloudContextService;
    this.userRequest = userRequest;
  }

  @Override
  public StepResult doStep(FlightContext flightContext)
      throws InterruptedException, RetryException {
    AwsCloudContext cloudContext =
        awsCloudContextService.getRequiredAwsCloudContext(resource.getWorkspaceId());

    AwsCredentialsProvider credentialsProvider =
        AwsUtils.createWsmCredentialProvider(
            awsCloudContextService.getRequiredAuthentication(),
            awsCloudContextService.discoverEnvironment());

    Collection<Tag> tags = new HashSet<>();
    AwsUtils.appendUserTags(tags, userRequest);
    AwsUtils.appendResourceTags(tags, cloudContext);

    AwsUtils.createFolder(
        credentialsProvider,
        Region.of(resource.getRegion()),
        resource.getBucketName(),
        resource.getPrefix(),
        tags);
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) throws InterruptedException {
    AwsCredentialsProvider credentialsProvider =
        AwsUtils.createWsmCredentialProvider(
            awsCloudContextService.getRequiredAuthentication(),
            awsCloudContextService.discoverEnvironment());

    AwsUtils.deleteFolder(
        credentialsProvider,
        Region.of(resource.getRegion()),
        resource.getBucketName(),
        resource.getPrefix());
    return StepResult.getStepResultSuccess();
  }
}
