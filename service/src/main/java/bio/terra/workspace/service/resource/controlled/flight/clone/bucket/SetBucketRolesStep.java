package bio.terra.workspace.service.resource.controlled.flight.clone.bucket;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.common.utils.GcpUtils;
import bio.terra.workspace.service.resource.controlled.ControlledGcsBucketResource;
import bio.terra.workspace.service.resource.model.CloningInstructions;
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
 */
public class SetBucketRolesStep implements Step {
  private static final List<String> DESTINATION_BUCKET_ROLE_NAMES =
      Stream.of("roles/storage.legacyBucketWriter").collect(Collectors.toList());
  private static final List<String> SOURCE_BUCKET_ROLE_NAMES =
      Stream.of("roles/storage.objectViewer", "roles/storage.legacyBucketReader")
          .collect(Collectors.toList());

  private final ControlledGcsBucketResource sourceBucket;
  private final GcpCloudContextService gcpCloudContextService;
  private final BucketCloneRolesComponent bucketCloneRolesService;

  public SetBucketRolesStep(
      ControlledGcsBucketResource sourceBucket,
      GcpCloudContextService gcpCloudContextService,
      BucketCloneRolesComponent bucketCloneRolesService) {
    this.sourceBucket = sourceBucket;
    this.gcpCloudContextService = gcpCloudContextService;
    this.bucketCloneRolesService = bucketCloneRolesService;
  }

  @Override
  public StepResult doStep(FlightContext flightContext)
      throws InterruptedException, RetryException {
    final FlightMap workingMap = flightContext.getWorkingMap();

    final CloningInstructions effectiveCloningInstructions =
        workingMap.get(ControlledResourceKeys.CLONING_INSTRUCTIONS, CloningInstructions.class);
    // This step is only run for full resource clones
    if (CloningInstructions.COPY_RESOURCE != effectiveCloningInstructions) {
      return StepResult.getStepResultSuccess();
    }

    // Gather bucket inputs and store them in the working map for the next
    // step.
    final BucketCloneInputs sourceInputs = getSourceInputs();
    workingMap.put(ControlledResourceKeys.SOURCE_CLONE_INPUTS, sourceInputs);

    final BucketCloneInputs destinationInputs = getDestinationInputs(flightContext);
    workingMap.put(ControlledResourceKeys.DESTINATION_CLONE_INPUTS, destinationInputs);

    final String controlPlaneProjectId = GcpUtils.getControlPlaneProjectId();
    workingMap.put(ControlledResourceKeys.CONTROL_PLANE_PROJECT_ID, controlPlaneProjectId);

    // Get the Storage Transfer Service
    final Storagetransfer storageTransferService;
    // Determine the Storage Transfer Service SA
    final String storageTransferServiceSAEmail;
    try {
      storageTransferService = StorageTransferServiceUtils.createStorageTransferService();
      storageTransferServiceSAEmail =
          getStorageTransferServiceSAEmail(storageTransferService, controlPlaneProjectId);
    } catch (IOException e) {
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY, e);
    }
    workingMap.put(
        ControlledResourceKeys.STORAGE_TRANSFER_SERVICE_SA_EMAIL, storageTransferServiceSAEmail);

    // Apply source and destination bucket roles
    bucketCloneRolesService.addBucketRoles(sourceInputs, storageTransferServiceSAEmail);
    bucketCloneRolesService.addBucketRoles(destinationInputs, storageTransferServiceSAEmail);

    return StepResult.getStepResultSuccess();
  }

  /**
   * Remove the roles from the buckets. The removeAllBucketRoles function is idempotent.
   *
   * @param flightContext
   * @return
   * @throws InterruptedException
   */
  @Override
  public StepResult undoStep(FlightContext flightContext) throws InterruptedException {
    bucketCloneRolesService.removeAllAddedBucketRoles(flightContext.getWorkingMap());
    return StepResult.getStepResultSuccess();
  }

  private BucketCloneInputs getSourceInputs() {
    final String sourceProjectId =
        gcpCloudContextService.getRequiredGcpProject(sourceBucket.getWorkspaceId());
    final String sourceBucketName = sourceBucket.getBucketName();
    return new BucketCloneInputs(
        sourceBucket.getWorkspaceId(), sourceProjectId, sourceBucketName, SOURCE_BUCKET_ROLE_NAMES);
  }

  private BucketCloneInputs getDestinationInputs(FlightContext flightContext) {
    // Not to be confused with the destination bucket name in the input parameters, which was
    // processed
    // in a previous step. This is the effective destination bucket name that was either
    // user-supplied
    // or generated randomly.
    final UUID workspaceId =
        flightContext
            .getInputParameters()
            .get(ControlledResourceKeys.DESTINATION_WORKSPACE_ID, UUID.class);
    final String projectId = gcpCloudContextService.getRequiredGcpProject(workspaceId);
    final String bucketName =
        flightContext
            .getWorkingMap()
            .get(ControlledResourceKeys.DESTINATION_BUCKET_NAME, String.class);
    return new BucketCloneInputs(workspaceId, projectId, bucketName, DESTINATION_BUCKET_ROLE_NAMES);
  }

  final String getStorageTransferServiceSAEmail(
      Storagetransfer storageTransferService, String controlPlaneProjectId) throws IOException {
    // Get the service account in the control plane project used by the transfer service to
    // perform the actual data transfer. It's named for and scoped to the project.
    return storageTransferService
        .googleServiceAccounts()
        .get(controlPlaneProjectId)
        .execute()
        .getAccountEmail();
  }
}
