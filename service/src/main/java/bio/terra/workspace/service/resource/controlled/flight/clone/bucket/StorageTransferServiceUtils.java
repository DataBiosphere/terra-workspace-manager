package bio.terra.workspace.service.resource.controlled.flight.clone.bucket;

import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.services.storagetransfer.v1.Storagetransfer;
import com.google.api.services.storagetransfer.v1.model.Date;
import com.google.api.services.storagetransfer.v1.model.GcsData;
import com.google.api.services.storagetransfer.v1.model.HttpData;
import com.google.api.services.storagetransfer.v1.model.Schedule;
import com.google.api.services.storagetransfer.v1.model.TransferJob;
import com.google.api.services.storagetransfer.v1.model.TransferOptions;
import com.google.api.services.storagetransfer.v1.model.TransferSpec;
import com.google.api.services.storagetransfer.v1.model.UpdateTransferJobRequest;
import com.google.common.annotations.VisibleForTesting;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class StorageTransferServiceUtils {
  private static final Logger logger = LoggerFactory.getLogger(StorageTransferServiceUtils.class);
  public static final String APPLICATION_NAME = "terra-workspace-manager";
  private static final String DELETED_STATUS = "DELETED";

  @VisibleForTesting public static final String ENABLED_STATUS = "ENABLED";

  @VisibleForTesting
  public static final String TRANSFER_JOB_DESCRIPTION =
      "Terra Workspace Manager Storage Transfer Job";

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

  /** A reusable step implementation for deleting a storage transfer job. */
  public static StepResult deleteTransferJobStepImpl(
      String flightId,
      @Nullable String transferJobName,
      String controlPlaneProjectId,
      Storagetransfer storagetransfer) {

    TransferJob transferJob;
    try {
      transferJob =
          StorageTransferServiceUtils.getTransferJob(
              storagetransfer, transferJobName, controlPlaneProjectId);
    } catch (IOException e) {
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_FATAL, e);
    }

    // transferJob == null means transfer job doesn't exist, and can happen if:
    // - doStep() failed before transfer job was created
    // - This undoStep() has run before
    // transferJob.getStatus() == "DELETED" can happen if:
    // - DeleteStorageTransferServiceJobStep.doStep() has succeeded. (Don't try to delete job again;
    //   that will result in dismal failure.)
    if (transferJob == null || transferJob.getStatus().equals("DELETED")) {
      return StepResult.getStepResultSuccess();
    }
    try {
      transferJobName =
          Optional.ofNullable(transferJobName).orElse(createTransferJobName(flightId));
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

  /** Create a one-time transfer job starting now. */
  public static void createTransferJob(
      Storagetransfer storagetransfer,
      TransferSpec transferSpec,
      String transferJobName,
      String controlPlaneProjectId)
      throws IOException {
    TransferJob transferJobInput =
        new TransferJob()
            .setName(transferJobName)
            .setDescription(TRANSFER_JOB_DESCRIPTION)
            .setProjectId(controlPlaneProjectId)
            .setSchedule(createScheduleRunOnceNow())
            .setTransferSpec(transferSpec)
            .setStatus(ENABLED_STATUS);
    // Create the TransferJob for the associated schedule and spec in the correct project.
    TransferJob transferJobOutput =
        storagetransfer.transferJobs().create(transferJobInput).execute();
    logger.debug("Created transfer job {}", transferJobOutput);
  }

  /**
   * Build a schedule to indicate that the job should run an operation immediately and not repeat
   * it.
   *
   * @return schedule object for the transfer job
   */
  private static Schedule createScheduleRunOnceNow() {
    final OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    final Date runDate =
        new Date().setYear(now.getYear()).setMonth(now.getMonthValue()).setDay(now.getDayOfMonth());
    // From the Javadoc: "If `schedule_end_date` and schedule_start_date are the same and
    // in the future relative to UTC, the transfer is executed only one time."
    //
    // Likewise, the doc for setStartTimeOfDay() states
    // "The time in UTC that a transfer job is scheduled to run. Transfers may start later than this
    // time. If `start_time_of_day` is not specified: * One-time transfers run immediately. *
    // Recurring transfers run immediately, and each day at midnight UTC, through schedule_end_date.
    //
    // Since our start and end days are the same, we get Run Once Now behavior.
    return new Schedule().setScheduleStartDate(runDate).setScheduleEndDate(runDate);
  }

  /** Create transfer spec from gcs bucket to gcs bucket. */
  public static TransferSpec createTransferSpecForSourceBucket(
      String sourceBucketName, String destinationBucketName) {
    return createTransferSpecCommon(destinationBucketName)
        .setGcsDataSource(new GcsData().setBucketName(sourceBucketName));
  }

  /** Create transfer spec from signed url list to gcs bucket. */
  public static TransferSpec createTransferSpecForSignedUrl(
      String url, String destinationBucketName) {
    var spec = createTransferSpecCommon(destinationBucketName);
    spec.setHttpDataSource(new HttpData().setListUrl(url));
    return spec;
  }

  private static TransferSpec createTransferSpecCommon(String destinationBucketName) {
    return new TransferSpec()
        .setGcsDataSink(new GcsData().setBucketName(destinationBucketName))
        .setTransferOptions(
            new TransferOptions()
                .setDeleteObjectsFromSourceAfterTransfer(false)
                .setOverwriteObjectsAlreadyExistingInSink(false));
  }

  /** Get the storage transfer service account from the control plane project. */
  public static String getStorageTransferServiceSAEmail(
      Storagetransfer storagetransfer, String controlPlaneProjectId) throws IOException {
    // Get the service account in the control plane project used by the transfer service to
    // perform the actual data transfer. It's named for and scoped to the project.
    return storagetransfer
        .googleServiceAccounts()
        .get(controlPlaneProjectId)
        .execute()
        .getAccountEmail();
  }

  /**
   * Return true if there is already a storage transfer job of the given name in the control plane.
   */
  public static boolean storageTransferJobExists(
      Storagetransfer storagetransfer, String transferJobName, String controlPlaneProjectId)
      throws IOException {
    try {
      TransferJob existingTransferJob =
          storagetransfer.transferJobs().get(transferJobName, controlPlaneProjectId).execute();
      if (existingTransferJob != null) {
        logger.info(
            "Transfer Job {} already exists. Nothing more for this step to do.", transferJobName);
        return true;
      }
    } catch (GoogleJsonResponseException e) {
      logger.info("No pre-existing transfer job named {} found.", transferJobName);
    }
    return false;
  }
}
