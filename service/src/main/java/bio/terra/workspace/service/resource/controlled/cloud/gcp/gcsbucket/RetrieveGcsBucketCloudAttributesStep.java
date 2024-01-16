package bio.terra.workspace.service.resource.controlled.cloud.gcp.gcsbucket;

import bio.terra.cloudres.google.storage.BucketCow;
import bio.terra.cloudres.google.storage.StorageCow;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.workspace.GcpCloudContextService;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import com.google.cloud.storage.BucketInfo;
import jakarta.ws.rs.BadRequestException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * For controlled GCS bucket resource clone, extract source bucket info from the cloud. This
 * information takes the form of one of two {@link RetrievalMode} values.
 *
 * <p>Preconditions: Source bucket exists in GCS.
 *
 * <p>Post-conditions: BucketInfo information from GCS is in the working map under
 * CREATION_PARAMETERS or PREVIOUS_UPDATE_PARAMETERS, depending on retrievalMode.
 */
public class RetrieveGcsBucketCloudAttributesStep implements Step {
  private static final Logger logger =
      LoggerFactory.getLogger(RetrieveGcsBucketCloudAttributesStep.class);
  private final ControlledGcsBucketResource bucketResource;
  private final CrlService crlService;
  private final GcpCloudContextService gcpCloudContextService;
  private final RetrievalMode retrievalMode;

  // TODO: PF-850 - just use creation parameters and remove retrieval mode.
  public enum RetrievalMode {
    UPDATE_PARAMETERS,
    CREATION_PARAMETERS
  }

  public RetrieveGcsBucketCloudAttributesStep(
      ControlledGcsBucketResource bucketResource,
      CrlService crlService,
      GcpCloudContextService gcpCloudContextService,
      RetrievalMode retrievalMode) {
    this.bucketResource = bucketResource;
    this.crlService = crlService;
    this.gcpCloudContextService = gcpCloudContextService;
    this.retrievalMode = retrievalMode;
  }

  @Override
  public StepResult doStep(FlightContext flightContext)
      throws InterruptedException, RetryException {
    final FlightMap workingMap = flightContext.getWorkingMap();
    final String projectId =
        gcpCloudContextService.getRequiredReadyGcpProject(bucketResource.getWorkspaceId());
    // get the storage cow
    final StorageCow storageCow = crlService.createStorageCow(projectId);

    // get the existing bucket cow
    final BucketCow existingBucketCow = storageCow.get(bucketResource.getBucketName());
    if (existingBucketCow == null) {
      logger.error(
          "Can't construct COW for pre-existing bucket {}", bucketResource.getBucketName());
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_FATAL, null);
    }

    // get the attributes
    final BucketInfo existingBucketInfo = existingBucketCow.getBucketInfo();
    switch (retrievalMode) {
      case UPDATE_PARAMETERS ->
          workingMap.put(
              ControlledResourceKeys.PREVIOUS_UPDATE_PARAMETERS,
              GcsApiConversions.toUpdateParameters(existingBucketInfo));
      case CREATION_PARAMETERS ->
          workingMap.put(
              ControlledResourceKeys.CREATION_PARAMETERS,
              GcsApiConversions.toCreationParameters(existingBucketInfo));
      default ->
          throw new BadRequestException(
              String.format("Unsupported Retrieval mode %s", retrievalMode));
    }

    return StepResult.getStepResultSuccess();
  }

  // Nothing to undo here
  @Override
  public StepResult undoStep(FlightContext flightContext) throws InterruptedException {
    return StepResult.getStepResultSuccess();
  }
}
