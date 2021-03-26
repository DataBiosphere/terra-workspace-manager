package bio.terra.workspace.service.resource.controlled.flight.delete;

import bio.terra.cloudres.google.storage.BucketCow;
import bio.terra.cloudres.google.storage.StorageCow;
import bio.terra.stairway.Flight;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.common.exception.SamApiException;
import bio.terra.workspace.common.utils.FlightBeanBag;
import bio.terra.workspace.db.ResourceDao;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.job.JobMapKeys;
import bio.terra.workspace.service.resource.WsmResource;
import bio.terra.workspace.service.resource.controlled.ControlledGcsBucketResource;
import bio.terra.workspace.service.resource.controlled.ControlledResource;
import bio.terra.workspace.service.resource.controlled.exception.BucketDeleteTimeoutException;
import bio.terra.workspace.service.workspace.WorkspaceService;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import com.google.cloud.storage.BucketInfo;
import com.google.cloud.storage.StorageException;
import com.google.common.collect.ImmutableList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

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

    final UUID workspaceId = inputParameters.get(WorkspaceFlightMapKeys.WORKSPACE_ID, UUID.class);
    final UUID resourceId =
        inputParameters.get(WorkspaceFlightMapKeys.ResourceKeys.RESOURCE_ID, UUID.class);
    final AuthenticatedUserRequest userRequest =
        inputParameters.get(JobMapKeys.AUTH_USER_INFO.getKeyName(), AuthenticatedUserRequest.class);

    // Flight plan:
    // 1. Delete the Sam resource. That will make the object inaccessible.
    // 2. Delete the cloud resource:
    //    a. Set the lifecycle on the bucket to delete immediately
    //    b. Try deleting the bucket
    //    c. If delete succeeds, finish step
    //    d. If delete fails, sleep one hour; goto (either a or b; maybe a for belts and suspenders)
    // 3. Delete the metadata
    addStep(
        new DeleteSamResourceStep(
            flightBeanBag.getResourceDao(),
            flightBeanBag.getSamService(),
            workspaceId,
            resourceId,
            userRequest));
    addStep(
        new DeleteGcsBucketStep(
            flightBeanBag.getCrlService(),
            flightBeanBag.getResourceDao(),
            flightBeanBag.getWorkspaceService(),
            workspaceId,
            resourceId));
    addStep(new DeleteMetadataStep(flightBeanBag.getResourceDao(), workspaceId, resourceId));
  }

  static class DeleteSamResourceStep implements Step {
    private final ResourceDao resourceDao;
    private final SamService samService;
    private final UUID workspaceId;
    private final UUID resourceId;
    private final AuthenticatedUserRequest userRequest;

    // TODO: this looks generic, so factor it out and share it
    public DeleteSamResourceStep(
        ResourceDao resourceDao,
        SamService samService,
        UUID workspaceId,
        UUID resourceId,
        AuthenticatedUserRequest userRequest) {
      this.resourceDao = resourceDao;
      this.samService = samService;
      this.workspaceId = workspaceId;
      this.resourceId = resourceId;
      this.userRequest = userRequest;
    }

    @Override
    public StepResult doStep(FlightContext flightContext)
        throws InterruptedException, RetryException {

      WsmResource wsmResource = resourceDao.getResource(workspaceId, resourceId);
      ControlledResource resource = wsmResource.castToControlledResource();

      try {
        samService.deleteControlledResource(resource, userRequest);
      } catch (SamApiException samApiException) {
        // The Sam resource might be not found for two reasons. First, the failure causing
        // this undo might be that we failed to create the resource. Second, if there is a
        // system failure during undo, this step might be called twice and might have already
        // done the resource delete.
        if (samApiException.getApiExceptionStatus() == HttpStatus.NOT_FOUND.value()) {
          logger.debug("No Sam resource found for resource {}", resourceId);
        } else {
          throw samApiException;
        }
      }
      return StepResult.getStepResultSuccess();
    }

    @Override
    public StepResult undoStep(FlightContext flightContext) throws InterruptedException {
      // No undo for delete. There is no way to put it back.
      return StepResult.getStepResultSuccess();
    }
  }

  // TODO: when Stairway implements timed waits, we can use those and not sit on a thread sleeping
  //  for three days.
  static class DeleteGcsBucketStep implements Step {
    private static final int MAX_DELETE_TRIES = 72; // 3 days
    private final CrlService crlService;
    private final ResourceDao resourceDao;
    private final WorkspaceService workspaceService;
    private final UUID workspaceId;
    private final UUID resourceId;

    public DeleteGcsBucketStep(
        CrlService crlService,
        ResourceDao resourceDao,
        WorkspaceService workspaceService,
        UUID workspaceId,
        UUID resourceId) {
      this.crlService = crlService;
      this.resourceDao = resourceDao;
      this.workspaceService = workspaceService;
      this.workspaceId = workspaceId;
      this.resourceId = resourceId;
    }

    @Override
    public StepResult doStep(FlightContext flightContext) throws InterruptedException {
      int deleteTries = 0;
      String projectId = workspaceService.getRequiredGcpProject(workspaceId);
      WsmResource wsmResource = resourceDao.getResource(workspaceId, resourceId);
      ControlledGcsBucketResource resource =
          wsmResource.castToControlledResource().castToGcsBucketResource();
      final StorageCow storageCow = crlService.createStorageCow(projectId);
      BucketCow bucket = storageCow.get(resource.getBucketName());

      boolean bucketExists = true;
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
      return StepResult.getStepResultSuccess();
    }
  }

  // TODO: this looks generic, so factor it out and share it
  static class DeleteMetadataStep implements Step {
    private final ResourceDao resourceDao;
    private final UUID workspaceId;
    private final UUID resourceId;

    public DeleteMetadataStep(ResourceDao resourceDao, UUID workspaceId, UUID resourceId) {
      this.resourceDao = resourceDao;
      this.workspaceId = workspaceId;
      this.resourceId = resourceId;
    }

    @Override
    public StepResult doStep(FlightContext flightContext)
        throws InterruptedException, RetryException {
      resourceDao.deleteResource(workspaceId, resourceId);
      return StepResult.getStepResultSuccess();
    }

    @Override
    public StepResult undoStep(FlightContext flightContext) throws InterruptedException {
      return StepResult.getStepResultSuccess();
    }
  }
}
