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
import com.google.cloud.storage.BucketInfo.LifecycleRule;
import com.google.cloud.storage.StorageClass;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.List;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SuppressFBWarnings(
    value = "RV_RETURN_VALUE_IGNORED_INFERRED",
    justification = "OK to ignore return value from BucketCow.update()")
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
    FlightMap inputParameters = flightContext.getInputParameters();
    ApiGcpGcsBucketUpdateParameters updateParameters =
        inputParameters.get(UPDATE_PARAMETERS, ApiGcpGcsBucketUpdateParameters.class);

    return updateBucket(updateParameters);
  }

  // Restore the previous values of the update parameters
  @Override
  public StepResult undoStep(FlightContext flightContext) throws InterruptedException {
    FlightMap workingMap = flightContext.getWorkingMap();
    ApiGcpGcsBucketUpdateParameters previousUpdateParameters =
        workingMap.get(PREVIOUS_UPDATE_PARAMETERS, ApiGcpGcsBucketUpdateParameters.class);

    return updateBucket(previousUpdateParameters);
  }

  private StepResult updateBucket(@Nullable ApiGcpGcsBucketUpdateParameters updateParameters) {
    if (updateParameters == null) {
      // nothing to change
      logger.info("No update parameters supplied, so no changes to make.");
      return StepResult.getStepResultSuccess();
    }
    String projectId =
        gcpCloudContextService.getRequiredGcpProject(bucketResource.getWorkspaceId());
    StorageCow storageCow = crlService.createStorageCow(projectId);

    BucketCow existingBucketCow = storageCow.get(bucketResource.getBucketName());
    if (existingBucketCow == null) {
      IllegalStateException isEx =
          new IllegalStateException(
              "No bucket found to update with name " + bucketResource.getBucketName());
      logger.error("No bucket found to update with name {}.", bucketResource.getBucketName(), isEx);
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_FATAL, isEx);
    }
    List<LifecycleRule> gcsLifecycleRules = toGcsApiRulesList(updateParameters.getLifecycle());
    // An empty array will clear all rules. A null array will take no effect. A populated array will
    // clear then set rules
    boolean doReplaceLifecycleRules = gcsLifecycleRules != null;

    // Lifecycle rules need to be cleared before being set. We should only do this if
    // we have changes.
    BucketCow.Builder bucketCowBuilder;
    if (doReplaceLifecycleRules) {
      var deleteBuilder = existingBucketCow.toBuilder();
      deleteBuilder.deleteLifecycleRules();
      var clearedRulesBucket = deleteBuilder.build().update();
      // do separate update to set the lifecycle rules
      bucketCowBuilder = clearedRulesBucket.toBuilder();
      bucketCowBuilder.setLifecycleRules(gcsLifecycleRules);
    } else {
      // do not delete the lifecycle rules, as they are not changing
      bucketCowBuilder = existingBucketCow.toBuilder();
    }

    StorageClass gcsStorageClass = toGcsApi(updateParameters.getDefaultStorageClass());
    boolean replaceStorageClass = gcsStorageClass != null;

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
