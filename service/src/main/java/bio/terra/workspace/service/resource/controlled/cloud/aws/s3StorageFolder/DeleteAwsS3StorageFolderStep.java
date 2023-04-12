package bio.terra.workspace.service.resource.controlled.cloud.aws.s3StorageFolder;

import static bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys.AWS_CLOUD_CONTEXT;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.common.utils.AwsUtils;
import bio.terra.workspace.common.utils.FlightUtils;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import bio.terra.workspace.service.workspace.model.AwsCloudContext;
import bio.terra.workspace.service.workspace.model.GcpCloudContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.regions.Region;

public class DeleteAwsS3StorageFolderStep implements Step {
  private static final Logger logger = LoggerFactory.getLogger(DeleteAwsS3StorageFolderStep.class);
  private final ControlledAwsS3StorageFolderResource resource;

  public DeleteAwsS3StorageFolderStep(ControlledAwsS3StorageFolderResource resource) {
    this.resource = resource;
  }

  @Override
  public StepResult doStep(FlightContext flightContext)
      throws InterruptedException, RetryException {


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
