package bio.terra.workspace.service.resource.controlled.cloud.gcp.gcsbucket;

import static bio.terra.workspace.service.resource.controlled.cloud.gcp.gcsbucket.GcsApiConversions.toGcsApi;
import static bio.terra.workspace.service.resource.controlled.cloud.gcp.gcsbucket.GcsApiConversions.toGcsApiRulesList;
import static bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys.PREVIOUS_UPDATE_PARAMETERS;
import static bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ResourceKeys.UPDATE_PARAMETERS;

import bio.terra.cloudres.google.storage.BucketCow;
import bio.terra.cloudres.google.storage.StorageCow;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.generated.model.ApiGcpGcsBucketUpdateParameters;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.workspace.GcpCloudContextService;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import com.google.cloud.storage.BucketInfo.LifecycleRule;
import com.google.cloud.storage.StorageClass;
import java.util.List;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UpdateGcsBucketStep implements Step {
  private final Logger logger = LoggerFactory.getLogger(UpdateGcsBucketStep.class);
  private final ControlledGcsBucketResource bucketResource;
  private final CrlService crlService;
  private final GcpCloudContextService gcpCloudContextService;

  public UpdateGcsBucketStep(
      ControlledGcsBucketResource bucketResource,
      CrlService crlService,
      GcpCloudContextService gcpCloudContextService) {
    this.bucketResource = bucketResource;
    this.crlService = crlService;
    this.gcpCloudContextService = gcpCloudContextService;
  }

  @Override
  public StepResult doStep(FlightContext flightContext)
      throws InterruptedException, RetryException {
    final FlightMap inputParameters = flightContext.getInputParameters();
    final ApiGcpGcsBucketUpdateParameters updateParameters =
        inputParameters.get(UPDATE_PARAMETERS, ApiGcpGcsBucketUpdateParameters.class);
    // Although the ready check is done in the controller, there is a chance this step will
    // fail because the cloud context is not ready. We do not want the undo to fail trying
    // to get the project id if that is the case. We store the project id in the working map.
    // If it is present, then the undo can use it. If not, the undo assumes that we failed
    // getting the project id and does not attempt to delete the bucket.
    // TODO: PF-2799 replace API update parameters of the bucket with a structure
    //  that includes the project id. Stop looking it up constantly.
    String projectId =
        gcpCloudContextService.getRequiredReadyGcpProject(bucketResource.getWorkspaceId());
    flightContext.getWorkingMap().put(WorkspaceFlightMapKeys.GCP_PROJECT_ID, projectId);

    return updateBucket(updateParameters, projectId);
  }

  // Restore the previous values of the update parameters
  @Override
  public StepResult undoStep(FlightContext flightContext) throws InterruptedException {
    final FlightMap workingMap = flightContext.getWorkingMap();
    final ApiGcpGcsBucketUpdateParameters previousUpdateParameters =
        workingMap.get(PREVIOUS_UPDATE_PARAMETERS, ApiGcpGcsBucketUpdateParameters.class);
    // Only attempt to undo the bucket update if we successfully retrieved the project id in the
    // Do().
    String projectId =
        flightContext.getWorkingMap().get(WorkspaceFlightMapKeys.GCP_PROJECT_ID, String.class);
    if (projectId != null) {
      return updateBucket(previousUpdateParameters, projectId);
    }
    return StepResult.getStepResultSuccess();
  }

  private StepResult updateBucket(
      @Nullable ApiGcpGcsBucketUpdateParameters updateParameters, String projectId) {
    if (updateParameters == null) {
      // nothing to change
      logger.info("No update parameters supplied, so no changes to make.");
      return StepResult.getStepResultSuccess();
    }
    StorageCow storageCow = crlService.createStorageCow(projectId);

    BucketCow existingBucketCow = storageCow.get(bucketResource.getBucketName());
    if (existingBucketCow == null) {
      IllegalStateException isEx =
          new IllegalStateException(
              "No bucket found to update with name " + bucketResource.getBucketName());
      logger.error("No bucket found to update with name {}.", bucketResource.getBucketName(), isEx);
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_FATAL, isEx);
    }
    final List<LifecycleRule> gcsLifecycleRules =
        toGcsApiRulesList(updateParameters.getLifecycle());
    // An empty array will clear all rules. A null array will take no effect. A populated array will
    // clear then set rules
    final boolean doReplaceLifecycleRules = gcsLifecycleRules != null;

    // Lifecycle rules need to be cleared before being set. We should only do this if
    // we have changes.
    final BucketCow.Builder bucketCowBuilder;
    if (doReplaceLifecycleRules) {
      final var deleteBuilder = existingBucketCow.toBuilder();
      deleteBuilder.deleteLifecycleRules();
      var clearedRulesBucket = deleteBuilder.build().update();
      // do separate update to set the lifecycle rules
      bucketCowBuilder = clearedRulesBucket.toBuilder();
      bucketCowBuilder.setLifecycleRules(gcsLifecycleRules);
    } else {
      // do not delete the lifecycle rules, as they are not changing
      bucketCowBuilder = existingBucketCow.toBuilder();
    }

    final StorageClass gcsStorageClass = toGcsApi(updateParameters.getDefaultStorageClass());
    final boolean replaceStorageClass = gcsStorageClass != null;

    if (replaceStorageClass) {
      bucketCowBuilder.setStorageClass(gcsStorageClass);
    }

    if (doReplaceLifecycleRules || replaceStorageClass) {
      bucketCowBuilder.build().update();
    } else {
      logger.info(
          "Cloud attributes for Bucket {} were not changed as all inputs were null.",
          bucketResource.getBucketName());
    }

    return StepResult.getStepResultSuccess();
  }
}
