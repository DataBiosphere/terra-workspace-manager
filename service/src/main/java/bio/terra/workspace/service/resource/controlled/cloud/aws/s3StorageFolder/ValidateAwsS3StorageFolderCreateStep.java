package bio.terra.workspace.service.resource.controlled.cloud.aws.s3StorageFolder;

import bio.terra.common.exception.ApiException;
import bio.terra.common.exception.ConflictException;
import bio.terra.common.exception.UnauthorizedException;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.common.utils.AwsUtils;
import bio.terra.workspace.common.utils.FlightUtils;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.workspace.AwsCloudContextService;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;

public class ValidateAwsS3StorageFolderCreateStep implements Step {
  private final ControlledAwsS3StorageFolderResource resource;
  private final AwsCloudContextService awsCloudContextService;
  private final SamService samService;

  public ValidateAwsS3StorageFolderCreateStep(
      ControlledAwsS3StorageFolderResource resource,
      AwsCloudContextService awsCloudContextService,
      SamService samService) {
    this.resource = resource;
    this.awsCloudContextService = awsCloudContextService;
    this.samService = samService;
  }

  @Override
  public StepResult doStep(FlightContext flightContext)
      throws InterruptedException, RetryException {
    AwsCredentialsProvider credentialsProvider =
        AwsUtils.createWsmCredentialProvider(
            awsCloudContextService.getRequiredAuthentication(),
            awsCloudContextService.discoverEnvironment(
                FlightUtils.getRequiredUserEmail(flightContext.getInputParameters(), samService)));

    try {
      if (AwsUtils.checkFolderExists(credentialsProvider, resource)) {
        return new StepResult(
            StepStatus.STEP_RESULT_FAILURE_FATAL,
            new ConflictException(
                String.format(
                    "Storage folder %s/ already exists in bucket %s.",
                    resource.getPrefix(), resource.getBucketName())));
      }
    } catch (ApiException | UnauthorizedException e) {
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_FATAL, e);
    }

    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) throws InterruptedException {
    return StepResult.getStepResultSuccess();
  }
}
