package bio.terra.workspace.service.resource.controlled.cloud.aws.s3StorageFolder;

import bio.terra.common.exception.ConflictException;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.common.utils.AwsUtils;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.workspace.AwsCloudContextService;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sts.model.Tag;

public class CreateAwsS3StorageFolderStep implements Step {
  private static final Logger logger = LoggerFactory.getLogger(CreateAwsS3StorageFolderStep.class);
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
    AwsCredentialsProvider credentialsProvider =
        AwsUtils.createWsmCredentialProvider(
            awsCloudContextService.getRequiredAuthentication(),
            awsCloudContextService.discoverEnvironment());

    if (AwsUtils.checkS3FolderExists(
        credentialsProvider,
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

    Set<Tag> tags =
        Stream.of(
                Tag.builder().key("user_email").value(userRequest.getEmail()).build(),
                Tag.builder().key("user_id").value(userRequest.getSubjectId()).build(),
                Tag.builder().key("ws_id").value(resource.getWorkspaceId().toString()).build())
            .collect(Collectors.toSet());

    AwsUtils.createS3Folder(
        credentialsProvider,
        Region.of(resource.getRegion()),
        resource.getS3BucketName(),
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

    AwsUtils.deleteS3Folder(
        credentialsProvider,
        Region.of(resource.getRegion()),
        resource.getS3BucketName(),
        resource.getPrefix());
    return StepResult.getStepResultSuccess();
  }
}
