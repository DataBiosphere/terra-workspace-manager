package bio.terra.workspace.service.resource.controlled.flight.clone.bucket;

import static bio.terra.workspace.service.resource.controlled.flight.clone.bucket.StorageTransferServiceUtils.createTransferJob;
import static bio.terra.workspace.service.resource.controlled.flight.clone.bucket.StorageTransferServiceUtils.createTransferSpecForSourceBucket;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.common.utils.FlightUtils;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.services.storagetransfer.v1.Storagetransfer;
import com.google.api.services.storagetransfer.v1.model.TransferJob;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Create an STS Job for transfer operations from the source bucket ot the destination.
 *
 * <p>Preconditions: Source and destination buckets exist, and have appropriate IAM roles in place
 * for the control plane service account. The working map contains SOURECE_CLONE_INPUTS,
 * DESTINATION_CLONE_INPUTS, CONTROL_PLAN_PROJECT_ID, and STORAGE_TRANSFER_SERVICE_SA_EMAIL.
 *
 * <p>Post conditions: A transfer job in the control plane project with a unique name for this
 * flight is created. It is scheduled to run once immediately.
 */
public final class TransferGcsBucketToGcsBucketStep implements Step {

  private static final Logger logger =
      LoggerFactory.getLogger(TransferGcsBucketToGcsBucketStep.class);

  private final Storagetransfer storagetransfer;

  public TransferGcsBucketToGcsBucketStep(Storagetransfer storagetransfer) {
    this.storagetransfer = storagetransfer;
  }

  // See https://cloud.google.com/storage-transfer/docs/reference/rest/v1/transferJobs/create
  // (somewhat dated) and
  // https://cloud.google.com/storage-transfer/docs/create-manage-transfer-program
  @Override
  public StepResult doStep(FlightContext flightContext)
      throws InterruptedException, RetryException {
    FlightMap workingMap = flightContext.getWorkingMap();
    FlightUtils.validateRequiredEntries(
        workingMap,
        ControlledResourceKeys.SOURCE_CLONE_INPUTS,
        ControlledResourceKeys.DESTINATION_STORAGE_TRANSFER_INPUTS,
        ControlledResourceKeys.CONTROL_PLANE_PROJECT_ID,
        ControlledResourceKeys.STORAGE_TRANSFER_SERVICE_SA_EMAIL);
    // Get source & destination bucket input values
    StorageTransferInput sourceInputs =
        workingMap.get(ControlledResourceKeys.SOURCE_CLONE_INPUTS, StorageTransferInput.class);
    StorageTransferInput destinationInputs =
        workingMap.get(
            ControlledResourceKeys.DESTINATION_STORAGE_TRANSFER_INPUTS, StorageTransferInput.class);
    logger.info(
        "Starting data copy from source bucket \n\t{}\nto destination\n\t{}",
        sourceInputs,
        destinationInputs);

    String transferJobName =
        StorageTransferServiceUtils.createTransferJobName(flightContext.getFlightId());
    workingMap.put(ControlledResourceKeys.STORAGE_TRANSFER_JOB_NAME, transferJobName);

    String controlPlaneProjectId =
        workingMap.get(ControlledResourceKeys.CONTROL_PLANE_PROJECT_ID, String.class);
    logger.info(
        "Creating transfer job named {} in project {}", transferJobName, controlPlaneProjectId);

    // Look up the transfer job by name. If it's found, it means we are restarting this step and
    // the job either has an operation in progress or completed (possibly failed).
    try {
      TransferJob existingTransferJob =
          storagetransfer.transferJobs().get(transferJobName, controlPlaneProjectId).execute();
      if (null != existingTransferJob) {
        logger.info(
            "Transfer Job {} already exists. Nothing more for this step to do.", transferJobName);
        return StepResult.getStepResultSuccess();
      }
    } catch (GoogleJsonResponseException e) {
      logger.info("No pre-existing transfer job named {} found.", transferJobName);
    } catch (IOException e) {
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY, e);
    }
    // Get the service account in the control plane project used by the transfer service to
    // perform the actual data transfer. It's named for and scoped to the project.
    // Storage transfer service itself is free, so there should be no charges to the control
    // plane project. The usual egress charges will be made on the source bucket.
    // TODO(PF-888): understand what happens when the source bucket is requester pays. We don't
    //   support requester pays right now for controlled gcs buckets, but it might be set on a
    //   referenced bucket.
    String transferServiceSAEmail =
        workingMap.get(ControlledResourceKeys.STORAGE_TRANSFER_SERVICE_SA_EMAIL, String.class);
    logger.debug("Storage Transfer Service SA: {}", transferServiceSAEmail);

    try {
      createTransferJob(
          storagetransfer,
          createTransferSpecForSourceBucket(
              sourceInputs.getBucketName(), destinationInputs.getBucketName()),
          transferJobName,
          controlPlaneProjectId);
    } catch (IOException e) {
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY, e);
    }

    return StepResult.getStepResultSuccess();
  }

  // Remove the Storage Transfer Job. Any data in the destination bucket will be deleted in the
  // previous step's undo method.
  @Override
  public StepResult undoStep(FlightContext flightContext) throws InterruptedException {
    FlightMap workingMap = flightContext.getWorkingMap();
    String transferJobName =
        workingMap.get(ControlledResourceKeys.STORAGE_TRANSFER_JOB_NAME, String.class);
    String controlPlaneProjectId =
        workingMap.get(ControlledResourceKeys.CONTROL_PLANE_PROJECT_ID, String.class);
    // A failure to delete will result in a DISMAL_FAILURE of the flight.
    return StorageTransferServiceUtils.deleteTransferJobStepImpl(
        flightContext.getFlightId(), transferJobName, controlPlaneProjectId, storagetransfer);
  }
}
