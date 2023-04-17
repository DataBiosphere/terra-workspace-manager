package bio.terra.workspace.service.resource.controlled.cloud.aws.storageFolder;

import bio.terra.common.exception.ConflictException;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.common.utils.AwsUtils;
import bio.terra.workspace.service.workspace.AwsCloudContextService;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.regions.Region;

public class ValidateAwsStorageFolderCreateStep implements Step {
  private final ControlledAwsStorageFolderResource resource;
  private final AwsCloudContextService awsCloudContextService;

  public ValidateAwsStorageFolderCreateStep(
      ControlledAwsStorageFolderResource resource, AwsCloudContextService awsCloudContextService) {
    this.resource = resource;
    this.awsCloudContextService = awsCloudContextService;
  }

  @Override
  public StepResult doStep(FlightContext flightContext)
      throws InterruptedException, RetryException {
    AwsCredentialsProvider credentialsProvider =
        AwsUtils.createWsmCredentialProvider(
            awsCloudContextService.getRequiredAuthentication(),
            awsCloudContextService.discoverEnvironment());

    if (AwsUtils.checkFolderExists(
        credentialsProvider,
        Region.of(resource.getRegion()),
        resource.getBucketName(),
        resource.getPrefix())) {
      return new StepResult(
          StepStatus.STEP_RESULT_FAILURE_FATAL,
          new ConflictException(
              String.format(
                  "Prefix '%s/' already exists in bucket '%s'.",
                  resource.getPrefix(), resource.getBucketName())));
    }
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) throws InterruptedException {
    return StepResult.getStepResultSuccess();
  }
}
