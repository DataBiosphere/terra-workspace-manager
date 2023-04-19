package bio.terra.workspace.service.resource.controlled.flight.clone.bucket;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.common.utils.FlightUtils;
import bio.terra.workspace.common.utils.GcpUtils;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.gcsbucket.ControlledGcsBucketResource;
import bio.terra.workspace.service.workspace.GcpCloudContextService;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import com.google.api.services.storagetransfer.v1.Storagetransfer;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
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
  private static final List<String> DESTINATION_BUCKET_ROLE_NAMES =
      Stream.of("roles/storage.legacyBucketWriter").collect(Collectors.toList());
  private static final List<String> SOURCE_BUCKET_ROLE_NAMES =
      Stream.of("roles/storage.objectViewer", "roles/storage.legacyBucketReader")
          .collect(Collectors.toList());

  private final ControlledGcsBucketResource sourceBucket;
  private final GcpCloudContextService gcpCloudContextService;
  private final BucketCloneRolesService bucketCloneRolesService;
  private final Storagetransfer storagetransfer;

  public SetBucketRolesStep(
      ControlledGcsBucketResource sourceBucket,
      GcpCloudContextService gcpCloudContextService,
      BucketCloneRolesService bucketCloneRolesService,
      Storagetransfer storagetransfer) {
    this.sourceBucket = sourceBucket;
    this.gcpCloudContextService = gcpCloudContextService;
    this.bucketCloneRolesService = bucketCloneRolesService;
    this.storagetransfer = storagetransfer;
  }

  @Override
  public StepResult doStep(FlightContext flightContext)
      throws InterruptedException, RetryException {
    FlightMap workingMap = flightContext.getWorkingMap();
    FlightUtils.validateRequiredEntries(
        flightContext.getInputParameters(), ControlledResourceKeys.DESTINATION_WORKSPACE_ID);

    // Gather bucket inputs and store them in the working map for the next
    // step.
    BucketCloneInputs sourceInputs = getSourceInputs();
    workingMap.put(ControlledResourceKeys.SOURCE_CLONE_INPUTS, sourceInputs);

    BucketCloneInputs destinationInputs = getDestinationInputs(flightContext);
    workingMap.put(ControlledResourceKeys.DESTINATION_CLONE_INPUTS, destinationInputs);

    String controlPlaneProjectId = GcpUtils.getControlPlaneProjectId();
    workingMap.put(ControlledResourceKeys.CONTROL_PLANE_PROJECT_ID, controlPlaneProjectId);

    // Determine the Storage Transfer Service SA
    String storageTransferServiceSAEmail;
    try {
      storageTransferServiceSAEmail = getStorageTransferServiceSAEmail(controlPlaneProjectId);
    } catch (IOException e) {
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY, e);
    }
    workingMap.put(
        ControlledResourceKeys.STORAGE_TRANSFER_SERVICE_SA_EMAIL, storageTransferServiceSAEmail);

    // Apply source and destination bucket roles
    bucketCloneRolesService.addBucketRoles(sourceInputs, storageTransferServiceSAEmail);
    bucketCloneRolesService.addBucketRoles(destinationInputs, storageTransferServiceSAEmail);

    // Validate the outputs, so we fail fast if one goes missing.
    FlightUtils.validateRequiredEntries(
        workingMap,
        ControlledResourceKeys.SOURCE_CLONE_INPUTS,
        ControlledResourceKeys.DESTINATION_CLONE_INPUTS,
        ControlledResourceKeys.CONTROL_PLANE_PROJECT_ID,
        ControlledResourceKeys.STORAGE_TRANSFER_SERVICE_SA_EMAIL);
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

  private BucketCloneInputs getSourceInputs() {
    String sourceProjectId =
        gcpCloudContextService.getRequiredGcpProject(sourceBucket.getWorkspaceId());
    String sourceBucketName = sourceBucket.getBucketName();
    return new BucketCloneInputs(
        sourceBucket.getWorkspaceId(), sourceProjectId, sourceBucketName, SOURCE_BUCKET_ROLE_NAMES);
  }

  private BucketCloneInputs getDestinationInputs(FlightContext flightContext) {
    // Not to be confused with the destination bucket name in the input parameters, which was
    // processed
    // in a previous step. This is the effective destination bucket name that was either
    // user-supplied
    // or generated randomly.
    UUID workspaceUuid =
        flightContext
            .getInputParameters()
            .get(ControlledResourceKeys.DESTINATION_WORKSPACE_ID, UUID.class);
    String projectId = gcpCloudContextService.getRequiredGcpProject(workspaceUuid);
    String bucketName =
        flightContext
            .getWorkingMap()
            .get(ControlledResourceKeys.DESTINATION_BUCKET_NAME, String.class);
    return new BucketCloneInputs(
        workspaceUuid, projectId, bucketName, DESTINATION_BUCKET_ROLE_NAMES);
  }

  String getStorageTransferServiceSAEmail(String controlPlaneProjectId) throws IOException {
    // Get the service account in the control plane project used by the transfer service to
    // perform the actual data transfer. It's named for and scoped to the project.
    return storagetransfer
        .googleServiceAccounts()
        .get(controlPlaneProjectId)
        .execute()
        .getAccountEmail();
  }
}
