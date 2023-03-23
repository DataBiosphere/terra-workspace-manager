package bio.terra.workspace.service.resource.controlled.flight.clone.bucket;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import com.google.api.services.storagetransfer.v1.Storagetransfer;
import com.google.api.services.storagetransfer.v1.model.TransferJob;
import com.google.api.services.storagetransfer.v1.model.UpdateTransferJobRequest;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class StorageTransferServiceUtils {
  private static final Logger logger = LoggerFactory.getLogger(StorageTransferServiceUtils.class);
  public static final String APPLICATION_NAME = "terra-workspace-manager";
  private static final String DELETED_STATUS = "DELETED";

  private StorageTransferServiceUtils() {}

  /**
   * Delete the transfer job, as we don't support reusing them.
   *
   * @param storageTransferService - transfer service
   * @param transferJobName - unidque name of the transfer job
   * @param controlPlaneProjectId - GCP project ID of the control plane
   * @throws IOException IOException
   */
  public static void deleteTransferJob(
      Storagetransfer storageTransferService, String transferJobName, String controlPlaneProjectId)
      throws IOException {
    // If there's no job  to delete, return early
    final TransferJob existingTransferJob =
        getTransferJob(storageTransferService, transferJobName, controlPlaneProjectId);
    if (existingTransferJob == null) {
      logger.info(
          "Transfer Job {} in project {} was not found when trying to delete it.",
          transferJobName,
          controlPlaneProjectId);
      return;
    }
    final TransferJob patchedTransferJob = new TransferJob().setStatus(DELETED_STATUS);
    final UpdateTransferJobRequest updateTransferJobRequest =
        new UpdateTransferJobRequest()
            .setUpdateTransferJobFieldMask("status")
            .setTransferJob(patchedTransferJob)
            .setProjectId(controlPlaneProjectId);
    final TransferJob deletedTransferJob =
        storageTransferService
            .transferJobs()
            .patch(transferJobName, updateTransferJobRequest)
            .execute();
    if (!DELETED_STATUS.equals(deletedTransferJob.getStatus())) {
      logger.warn("Failed to delete transfer job {}", deletedTransferJob.getName());
    }
  }

  /**
   * A reusable step implementation for deleting a storage transfer job.
   *
   * @param flightContext flightContext
   * @return StepResult
   */
  public static StepResult deleteTransferJobStepImpl(
      FlightContext flightContext, Storagetransfer storagetransfer) {
    try {
      final String transferJobName =
          createTransferJobName(flightContext.getFlightId()); // might not be in map yet
      final String controlPlaneProjectId =
          flightContext
              .getWorkingMap()
              .get(ControlledResourceKeys.CONTROL_PLANE_PROJECT_ID, String.class);
      deleteTransferJob(storagetransfer, transferJobName, controlPlaneProjectId);
    } catch (IOException e) {
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_FATAL, e);
    }
    return StepResult.getStepResultSuccess();
  }

  public static TransferJob getTransferJob(
      Storagetransfer storageTransferService, String transferJobName, String controlPlaneProjectId)
      throws IOException {
    return storageTransferService
        .transferJobs()
        .get(transferJobName, controlPlaneProjectId)
        .execute();
  }

  /**
   * Construct the name to use for the transfer job, which must be globally unique. Use the flight
   * ID for the job name so we can find it after a restart.
   *
   * @param flightId - random ID for this flight
   * @return - the job name
   */
  public static String createTransferJobName(String flightId) {
    return "transferJobs/wsm-" + flightId;
  }
}
