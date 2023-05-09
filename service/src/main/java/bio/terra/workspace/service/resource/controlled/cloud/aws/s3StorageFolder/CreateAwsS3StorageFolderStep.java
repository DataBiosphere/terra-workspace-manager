package bio.terra.workspace.service.resource.controlled.cloud.aws.s3StorageFolder;

import bio.terra.common.iam.SamUser;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.common.utils.AwsUtils;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.SamService;
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
  private final SamService samService;

  public CreateAwsS3StorageFolderStep(
      ControlledAwsS3StorageFolderResource resource,
      AwsCloudContextService awsCloudContextService,
      AuthenticatedUserRequest userRequest,
      SamService samService) {
    this.resource = resource;
    this.awsCloudContextService = awsCloudContextService;
    this.userRequest = userRequest;
    this.samService = samService;
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

    SamUser samUser = samService.getSamUser(userRequest);
    Collection<Tag> tags = new HashSet<>();
    AwsUtils.appendUserTags(tags, samUser);
    AwsUtils.appendResourceTags(tags, cloudContext, resource);

    AwsUtils.createStorageFolder(credentialsProvider, resource, tags);
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) throws InterruptedException {
    AwsCredentialsProvider credentialsProvider =
        AwsUtils.createWsmCredentialProvider(
            awsCloudContextService.getRequiredAuthentication(),
            awsCloudContextService.discoverEnvironment());

    AwsUtils.deleteStorageFolder(
        credentialsProvider,
        Region.of(resource.getRegion()),
        resource.getBucketName(),
        resource.getPrefix());
    return StepResult.getStepResultSuccess();
  }
}
