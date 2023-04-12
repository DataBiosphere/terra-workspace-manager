package bio.terra.workspace.service.resource.controlled.cloud.aws.s3StorageFolder;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.common.utils.FlightUtils;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import bio.terra.workspace.service.workspace.model.AwsCloudContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CreateAwsS3StorageFolderStep implements Step {
  private static final Logger logger = LoggerFactory.getLogger(CreateAwsS3StorageFolderStep.class);
  private final ControlledAwsS3StorageFolderResource resource;

  public CreateAwsS3StorageFolderStep(ControlledAwsS3StorageFolderResource resource) {
    this.resource = resource;
  }

  @Override
  public StepResult doStep(FlightContext flightContext)
      throws InterruptedException, RetryException {
    FlightMap inputParameters = flightContext.getInputParameters();

    final AwsCloudContext awsCloudContext =
        FlightUtils.getRequired(
            flightContext.getWorkingMap(),
            ControlledResourceKeys.AWS_CLOUD_CONTEXT,
            AwsCloudContext.class);

    /*
    Credentials awsCredentials = MultiCloudUtils.assumeAwsServiceRoleFromGcp(awsCloudContext);
    AwsUtils.deleteFolder(
        awsCredentials,
        Region.of(resource.getRegion()),
        resource.getS3BucketName(),
        resource.getPrefix());
    // TODO-Dex
     */

    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) throws InterruptedException {
    logger.error(
        "Cannot undo delete of AWS S3 Storage Folder resource {} in workspace {}.",
        resource.getResourceId(),
        resource.getWorkspaceId());
    // Surface whatever error caused Stairway to begin undoing.
    return flightContext.getResult();
  }
}
