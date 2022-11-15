package bio.terra.workspace.service.resource.controlled.cloud.gcp.gcsbucket;

import bio.terra.cloudres.google.storage.BucketCow;
import bio.terra.cloudres.google.storage.StorageCow;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.common.exception.InternalLogicException;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.resource.controlled.flight.newclone.workspace.ControlledGcsBucketParameters;
import bio.terra.workspace.service.workspace.GcpCloudContextService;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import com.google.cloud.storage.BucketInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Retrieve information about the GCS cloud resource for use in update and clone Store the result in
 * working map RESOURCE_PARAMETERS key
 */
public class RetrieveGcsBucketParametersStep implements Step {
  private static final Logger logger =
      LoggerFactory.getLogger(RetrieveGcsBucketParametersStep.class);
  private final ControlledGcsBucketResource bucketResource;
  private final CrlService crlService;
  private final GcpCloudContextService gcpCloudContextService;

  public RetrieveGcsBucketParametersStep(
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
    FlightMap workingMap = flightContext.getWorkingMap();
    String projectId =
        gcpCloudContextService.getRequiredGcpProject(bucketResource.getWorkspaceId());
    StorageCow storageCow = crlService.createStorageCow(projectId);
    final BucketCow existingBucketCow = storageCow.get(bucketResource.getBucketName());
    if (existingBucketCow == null) {
      return new StepResult(
          StepStatus.STEP_RESULT_FAILURE_FATAL,
          new InternalLogicException(
              "Can't construct COW for pre-existing bucket " + bucketResource.getBucketName()));
    }

    BucketInfo existingBucketInfo = existingBucketCow.getBucketInfo();
    var bucketParameters =
        new ControlledGcsBucketParameters()
            .setBucketName(bucketResource.getBucketName())
            .setLocation(existingBucketInfo.getLocation())
            .setLifecycleRules(existingBucketInfo.getLifecycleRules())
            .setStorageClass(existingBucketInfo.getStorageClass());

    workingMap.put(ControlledResourceKeys.RESOURCE_PARAMETERS, bucketParameters);

    return StepResult.getStepResultSuccess();
  }

  // Nothing to undo here
  @Override
  public StepResult undoStep(FlightContext flightContext) throws InterruptedException {
    return StepResult.getStepResultSuccess();
  }
}
