package bio.terra.workspace.service.resource.controlled.flight.clone.bucket;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.common.utils.FlightUtils;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.gcsbucket.ControlledGcsBucketResource;
import bio.terra.workspace.service.workspace.GcpCloudContextService;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import com.google.api.services.storagetransfer.v1.Storagetransfer;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Give the Storage Transfer Service SA the appropriate roles on the source and destination (sink)
 * buckets to allow a transfer job to be created.
 *
 * <p>Preconditions: Destination bucket is created. Working map contains DESTINATION_WORKSPACE_ID.
 *
 * <p>Post conditions: Working map updated with SOURCE_CLONE_INPUTS, DESTINATION_CLONE_INPUTS,
 * CONTROL_PLANE_PROJECT_ID, and STORAGE_TRANSFER_SERVICE_SA_EMAIL. IAM roles are added to both the
 * source and destination buckets in GCS that will allow the transfer service SA the necessary
 * access to create a Storage Transfer Service Job.
 */
public class SetBucketRolesStep implements Step {

  private final BucketCloneRolesService bucketCloneRolesService;

  public SetBucketRolesStep(
      BucketCloneRolesService bucketCloneRolesService) {
    this.bucketCloneRolesService = bucketCloneRolesService;
  }

  @Override
  public StepResult doStep(FlightContext flightContext)
      throws InterruptedException, RetryException {
    FlightMap workingMap = flightContext.getWorkingMap();
    FlightUtils.validateRequiredEntries(
        flightContext.getInputParameters(), ControlledResourceKeys.DESTINATION_WORKSPACE_ID);
    // Validate the outputs, so we fail fast if one goes missing.
    FlightUtils.validateRequiredEntries(
        workingMap,
        ControlledResourceKeys.DESTINATION_STORAGE_TRANSFER_INPUTS,
        ControlledResourceKeys.STORAGE_TRANSFER_SERVICE_SA_EMAIL);


    var storageTransferServiceSAEmail = workingMap.get(ControlledResourceKeys.STORAGE_TRANSFER_SERVICE_SA_EMAIL, String.class);

    StorageTransferInput sourceInputs = workingMap.get(ControlledResourceKeys.SOURCE_CLONE_INPUTS, StorageTransferInput.class);
    StorageTransferInput destinationInputs = workingMap.get(ControlledResourceKeys.DESTINATION_STORAGE_TRANSFER_INPUTS, StorageTransferInput.class);

    // Apply source and destination bucket roles
    if (sourceInputs != null) {
      bucketCloneRolesService.addBucketRoles(sourceInputs, storageTransferServiceSAEmail);
    }
    bucketCloneRolesService.addBucketRoles(destinationInputs, storageTransferServiceSAEmail);

    return StepResult.getStepResultSuccess();
  }

  /**
   * Remove the roles from the buckets. The removeAllBucketRoles function is idempotent.
   *
   * @param flightContext flightContext
   * @return StepResult
   * @throws InterruptedException InterruptedException
   */
  @Override
  public StepResult undoStep(FlightContext flightContext) throws InterruptedException {
    bucketCloneRolesService.removeAllAddedBucketRoles(flightContext.getWorkingMap());
    return StepResult.getStepResultSuccess();
  }
}
