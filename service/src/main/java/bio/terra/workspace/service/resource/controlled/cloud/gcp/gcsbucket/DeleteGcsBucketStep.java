package bio.terra.workspace.service.resource.controlled.cloud.gcp.gcsbucket;

import bio.terra.cloudres.google.storage.BucketCow;
import bio.terra.cloudres.google.storage.StorageCow;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.workspace.common.utils.FlightUtils;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.resource.controlled.exception.BucketDeleteTimeoutException;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import bio.terra.workspace.service.workspace.model.GcpCloudContext;
import com.google.cloud.storage.BucketInfo;
import com.google.cloud.storage.StorageException;
import com.google.common.collect.ImmutableList;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A step for deleting a controlled GCS bucket resource. This step uses the following process to
 * actually delete the bucket: a. Set the lifecycle on the bucket to delete immediately b. Try
 * deleting the bucket c. If delete succeeds, finish step d. If delete fails, sleep one hour; goto
 * (either a or b; maybe a for belts and suspenders)
 *
 * <p>As this may take hours to days to complete, this step should never run as part of a
 * synchronous flight.
 */
// TODO: when Stairway implements timed waits, we can use those and not sit on a thread sleeping
//  for three days.
public class DeleteGcsBucketStep implements Step {
  private static final int MAX_DELETE_TRIES = 72; // 3 days
  private final CrlService crlService;
  private final ControlledGcsBucketResource resource;

  private final Logger logger = LoggerFactory.getLogger(DeleteGcsBucketStep.class);

  public DeleteGcsBucketStep(ControlledGcsBucketResource resource, CrlService crlService) {
    this.crlService = crlService;
    this.resource = resource;
  }

  @Override
  public StepResult doStep(FlightContext flightContext) throws InterruptedException {
    int deleteTries = 0;
    final GcpCloudContext gcpCloudContext =
        FlightUtils.getRequired(
            flightContext.getWorkingMap(),
            ControlledResourceKeys.GCP_CLOUD_CONTEXT,
            GcpCloudContext.class);
    final StorageCow storageCow =
        crlService.createWsmSaStorageCow(gcpCloudContext.getGcpProjectId());

    // If the bucket is already deleted (e.g. this step is being retried), storageCow.get() will
    // return null.
    BucketCow bucket = storageCow.get(resource.getBucketName());
    boolean bucketExists = bucket != null;
    while (bucketExists) {
      // We always replace the lifecycle rules. This covers the case where the step is rerun
      // and covers the case where the rules are changed out of band of this operation.
      BucketCow bucketCow =
          bucket.toBuilder()
              .setLifecycleRules(
                  ImmutableList.of(
                      new BucketInfo.LifecycleRule(
                          BucketInfo.LifecycleRule.LifecycleAction.newDeleteAction(),
                          BucketInfo.LifecycleRule.LifecycleCondition.newBuilder()
                              .setAge(0)
                              .build())))
              .build();
      bucket = bucketCow.update();
      bucketExists = tryBucketDelete(bucket);
      if (bucketExists) {
        TimeUnit.HOURS.sleep(1);
      }
      deleteTries++;
      if (deleteTries >= MAX_DELETE_TRIES) {
        // This will cause the flight to fail.
        throw new BucketDeleteTimeoutException(
            String.format("Failed to delete bucket after %d tries", MAX_DELETE_TRIES));
      }
    }
    return StepResult.getStepResultSuccess();
  }

  /**
   * Try deleting the bucket. It will fail if there are objects still in the bucket.
   *
   * @param bucket bucket we should try to delete
   * @return bucket existence: true if the bucket still exists; false if we deleted it
   */
  private boolean tryBucketDelete(BucketCow bucket) {
    try {
      logger.info("Attempting to delete bucket " + bucket.getBucketInfo().getName());
      bucket.delete();
      return false;
    } catch (StorageException ex) {
      logger.info(
          "Attempt to delete bucket failed on this try: " + bucket.getBucketInfo().getName(), ex);
      return true;
    }
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) throws InterruptedException {
    logger.error(
        "Cannot undo delete of GCS bucket resource {} in workspace {}.",
        resource.getResourceId(),
        resource.getWorkspaceId());
    // Surface whatever error caused Stairway to begin undoing.
    return flightContext.getResult();
  }
}
