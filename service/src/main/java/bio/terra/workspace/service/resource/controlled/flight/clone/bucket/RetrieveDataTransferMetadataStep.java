package bio.terra.workspace.service.resource.controlled.flight.clone.bucket;

import static bio.terra.workspace.service.resource.controlled.flight.clone.bucket.StorageTransferServiceUtils.getStorageTransferServiceSAEmail;

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
import javax.annotation.Nullable;

public class RetrieveDataTransferMetadataStep implements Step {

  private static final List<String> SOURCE_BUCKET_ROLE_NAMES =
      Stream.of("roles/storage.objectViewer", "roles/storage.legacyBucketReader")
          .collect(Collectors.toList());
  private static final List<String> DESTINATION_BUCKET_ROLE_NAMES =
      Stream.of("roles/storage.legacyBucketWriter").collect(Collectors.toList());
  private final Storagetransfer storagetransfer;
  private final GcpCloudContextService gcpCloudContextService;
  private final ControlledGcsBucketResource sourceBucket;

  public RetrieveDataTransferMetadataStep(
      Storagetransfer storagetransfer,
      GcpCloudContextService gcpCloudContextService,
      @Nullable ControlledGcsBucketResource sourceBucket) {
    this.storagetransfer = storagetransfer;
    this.gcpCloudContextService = gcpCloudContextService;
    this.sourceBucket = sourceBucket;
  }

  @Override
  public StepResult doStep(FlightContext context) throws InterruptedException, RetryException {
    FlightMap workingMap = context.getWorkingMap();

    String controlPlaneProjectId = GcpUtils.getControlPlaneProjectId();
    workingMap.put(ControlledResourceKeys.CONTROL_PLANE_PROJECT_ID, controlPlaneProjectId);

    String storageTransferServiceSAEmail;
    try {
      storageTransferServiceSAEmail =
          getStorageTransferServiceSAEmail(storagetransfer, controlPlaneProjectId);
    } catch (IOException e) {
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY, e);
    }
    workingMap.put(
        ControlledResourceKeys.STORAGE_TRANSFER_SERVICE_SA_EMAIL, storageTransferServiceSAEmail);

    // Destination bucket (sink)
    StorageTransferInput destinationInputs = getDestinationInputs(context);
    workingMap.put(ControlledResourceKeys.DESTINATION_STORAGE_TRANSFER_INPUTS, destinationInputs);
    // If the source is a gcs bucket, retrieve the metadata of the source bucket.
    if (sourceBucket != null) {
      StorageTransferInput sourceInputs = getSourceInputs(sourceBucket);
      workingMap.put(ControlledResourceKeys.SOURCE_CLONE_INPUTS, sourceInputs);
    }
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    // Nothing to undo.
    return StepResult.getStepResultSuccess();
  }

  private StorageTransferInput getDestinationInputs(FlightContext flightContext) {
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
        FlightUtils.getInputParameterOrWorkingValue(
            flightContext,
            /*inputKey=*/ ControlledResourceKeys.DESTINATION_BUCKET_NAME_FOR_SIGNED_URL_LIST,
            /*workingKey=*/ ControlledResourceKeys.DESTINATION_BUCKET_NAME,
            String.class);
    return new StorageTransferInput(
        workspaceUuid, projectId, bucketName, DESTINATION_BUCKET_ROLE_NAMES);
  }

  private StorageTransferInput getSourceInputs(ControlledGcsBucketResource sourceBucket) {
    String sourceProjectId =
        gcpCloudContextService.getRequiredGcpProject(sourceBucket.getWorkspaceId());
    String sourceBucketName = sourceBucket.getBucketName();
    return new StorageTransferInput(
        sourceBucket.getWorkspaceId(), sourceProjectId, sourceBucketName, SOURCE_BUCKET_ROLE_NAMES);
  }
}
