package bio.terra.workspace.service.resource.controlled.flight.delete;

import bio.terra.cloudres.google.storage.BucketCow;
import bio.terra.cloudres.google.storage.StorageCow;
import bio.terra.stairway.Flight;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.common.utils.FlightBeanBag;
import bio.terra.workspace.db.ResourceDao;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.resource.WsmResource;
import bio.terra.workspace.service.resource.controlled.ControlledGcsBucketResource;
import bio.terra.workspace.service.resource.controlled.exception.BucketDeleteTimeoutException;
import bio.terra.workspace.service.workspace.WorkspaceService;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import com.google.cloud.storage.BucketInfo;
import com.google.cloud.storage.StorageException;
import com.google.common.collect.ImmutableList;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Flight for deletion of a Gcs Bucket resource. This resource is deleted in a particular way and
 * can take a long time, so it gets its own flight.
 */
public class DeleteControlledResourceGcsBucketFlight extends Flight {
  private static final Logger logger =
      LoggerFactory.getLogger(DeleteControlledResourceGcsBucketFlight.class);

  public DeleteControlledResourceGcsBucketFlight(FlightMap inputParameters, Object beanBag) {
    super(inputParameters, beanBag);
    final FlightBeanBag flightBeanBag = FlightBeanBag.getFromObject(beanBag);

    // Flight plan:
    // 1. TODO: Delete the Sam resource - this should make the object invisible via get and
    // enumerate endpoints
    // 2. Delete the cloud resource:
    //    a. Set the lifecycle on the bucket to delete immediately
    //    b. Try deleting the bucket
    //    c. If delete succeeds, finish step
    //    d. If delete fails, sleep one hour; goto (either a or b; maybe a for belts and suspenders)
    // 3. Delete the metadata
    addStep(
        new DeleteGcsBucketStep(
            flightBeanBag.getCrlService(),
            flightBeanBag.getResourceDao(),
            flightBeanBag.getWorkspaceService()));
    addStep(new DeleteMetadataStep(flightBeanBag.getResourceDao()));
  }

  // TODO: when Stairway implements timed waits, we can use those and not sit on a thread sleeping
  // for three days.
  static class DeleteGcsBucketStep implements Step {
    private static final int MAX_DELETE_TRIES = 72; // 3 days
    private final CrlService crlService;
    private final ResourceDao resourceDao;
    private final WorkspaceService workspaceService;

    public DeleteGcsBucketStep(
        CrlService crlService, ResourceDao resourceDao, WorkspaceService workspaceService) {
      this.crlService = crlService;
      this.resourceDao = resourceDao;
      this.workspaceService = workspaceService;
    }

    @Override
    public StepResult doStep(FlightContext flightContext) throws InterruptedException {
      int deleteTries = 0;

      FlightMap inputParameters = flightContext.getInputParameters();
      UUID workspaceId =
          UUID.fromString(inputParameters.get(WorkspaceFlightMapKeys.WORKSPACE_ID, String.class));
      UUID resourceId =
          UUID.fromString(
              inputParameters.get(WorkspaceFlightMapKeys.ResourceKeys.RESOURCE_ID, String.class));

      String projectId = workspaceService.getGcpProject(workspaceId);

      WsmResource wsmResource = resourceDao.getResource(workspaceId, resourceId);
      ControlledGcsBucketResource resource =
          wsmResource.castControlledResource().castToGcsBucketResource();

      final StorageCow storageCow = crlService.createStorageCow(projectId);

      BucketCow bucket = storageCow.get(resource.getBucketName());

      boolean bucketExists = true;
      while (bucketExists) {
        // We always replace the lifecycle rules. This covers the case where the step is rerun
        // and covers the case where the rules are changed out of band of this operation.
        bucket.toBuilder()
            .setLifecycleRules(
                ImmutableList.of(
                    new BucketInfo.LifecycleRule(
                        BucketInfo.LifecycleRule.LifecycleAction.newDeleteAction(),
                        BucketInfo.LifecycleRule.LifecycleCondition.newBuilder()
                            .setAge(0)
                            .build())))
            .build()
            .update();
        bucketExists = tryBucketDelete(bucket);
        if (bucketExists) {
          TimeUnit.HOURS.sleep(1);
        }
        deleteTries++;
        if (deleteTries >= MAX_DELETE_TRIES) {
          // This will cause the flight to fail.
          throw new BucketDeleteTimeoutException(
              String.format("Failed to delete bucket after %d hours", MAX_DELETE_TRIES));
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
            "Attempt to delete bucket "
                + bucket.getBucketInfo().getName()
                + "failed, but that is OK we will try again",
            ex);
        return true;
      }
    }

    @Override
    public StepResult undoStep(FlightContext flightContext) throws InterruptedException {
      return StepResult.getStepResultSuccess();
    }
  }

  // TODO: this looks generic, so factor it out and share it
  static class DeleteMetadataStep implements Step {
    private final ResourceDao resourceDao;

    public DeleteMetadataStep(ResourceDao resourceDao) {
      this.resourceDao = resourceDao;
    }

    @Override
    public StepResult doStep(FlightContext flightContext)
        throws InterruptedException, RetryException {
      FlightMap inputParameters = flightContext.getInputParameters();
      UUID workspaceId =
          UUID.fromString(inputParameters.get(WorkspaceFlightMapKeys.WORKSPACE_ID, String.class));
      UUID resourceId =
          UUID.fromString(
              inputParameters.get(WorkspaceFlightMapKeys.ResourceKeys.RESOURCE_ID, String.class));
      resourceDao.deleteResource(workspaceId, resourceId);
      return StepResult.getStepResultSuccess();
    }

    @Override
    public StepResult undoStep(FlightContext flightContext) throws InterruptedException {
      return StepResult.getStepResultSuccess();
    }
  }
}
