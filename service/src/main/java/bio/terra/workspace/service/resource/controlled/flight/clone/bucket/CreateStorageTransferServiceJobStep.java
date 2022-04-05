package bio.terra.workspace.service.resource.controlled.flight.clone.bucket;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.common.utils.FlightUtils;
import bio.terra.workspace.service.resource.model.CloningInstructions;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.services.storagetransfer.v1.Storagetransfer;
import com.google.api.services.storagetransfer.v1.model.Date;
import com.google.api.services.storagetransfer.v1.model.GcsData;
import com.google.api.services.storagetransfer.v1.model.Schedule;
import com.google.api.services.storagetransfer.v1.model.TransferJob;
import com.google.api.services.storagetransfer.v1.model.TransferOptions;
import com.google.api.services.storagetransfer.v1.model.TransferSpec;
import com.google.common.annotations.VisibleForTesting;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class CreateStorageTransferServiceJobStep implements Step {

  private static final Logger logger =
      LoggerFactory.getLogger(CreateStorageTransferServiceJobStep.class);
  @VisibleForTesting public static final String ENABLED_STATUS = "ENABLED";
  @VisibleForTesting public static final String TRANSFER_JOB_DESCRIPTION = "Terra Workspace Manager Clone GCS Bucket";
  private final Storagetransfer storageTransferService;
  public CreateStorageTransferServiceJobStep(Storagetransfer storagetransfer) {
    this.storageTransferService = storagetransfer;
  }

  // See https://cloud.google.com/storage-transfer/docs/reference/rest/v1/transferJobs/create
  // (somewhat dated) and
  // https://cloud.google.com/storage-transfer/docs/create-manage-transfer-program
  @Override
  public StepResult doStep(FlightContext flightContext)
      throws InterruptedException, RetryException {
    final FlightMap workingMap = flightContext.getWorkingMap();
    FlightUtils.validateRequiredEntries(workingMap,
        ControlledResourceKeys.CLONING_INSTRUCTIONS,
        ControlledResourceKeys.SOURCE_CLONE_INPUTS,
        ControlledResourceKeys.DESTINATION_CLONE_INPUTS,
        ControlledResourceKeys.CONTROL_PLANE_PROJECT_ID,
        ControlledResourceKeys.STORAGE_TRANSFER_SERVICE_SA_EMAIL);
    final CloningInstructions effectiveCloningInstructions =
        workingMap.get(ControlledResourceKeys.CLONING_INSTRUCTIONS, CloningInstructions.class);
    // This step is only run for full resource clones
    if (CloningInstructions.COPY_RESOURCE != effectiveCloningInstructions) {
      return StepResult.getStepResultSuccess();
    }

    // Get source & destination bucket input values
    final BucketCloneInputs sourceInputs =
        workingMap.get(ControlledResourceKeys.SOURCE_CLONE_INPUTS, BucketCloneInputs.class);
    final BucketCloneInputs destinationInputs =
        workingMap.get(ControlledResourceKeys.DESTINATION_CLONE_INPUTS, BucketCloneInputs.class);
    logger.info(
        "Starting data copy from source bucket \n\t{}\nto destination\n\t{}",
        sourceInputs,
        destinationInputs);

    final String transferJobName =
        StorageTransferServiceUtils.createTransferJobName(flightContext.getFlightId());
    workingMap.put(ControlledResourceKeys.STORAGE_TRANSFER_JOB_NAME, transferJobName);

    final String controlPlaneProjectId =
        workingMap.get(ControlledResourceKeys.CONTROL_PLANE_PROJECT_ID, String.class);
    logger.info(
        "Creating transfer job named {} in project {}", transferJobName, controlPlaneProjectId);

    // Look up the transfer job by name. If it's found, it means we are restarting this step and
    // the job either has an operation in progress or completed (possibly failed).
    try {
      final TransferJob existingTransferJob =
          storageTransferService
              .transferJobs()
              .get(transferJobName, controlPlaneProjectId)
              .execute();
      if (null != existingTransferJob) {
        logger.info(
            "Transfer Job {} already exists. Nothing more for this step to do.", transferJobName);
        return StepResult.getStepResultSuccess();
      }
    } catch (GoogleJsonResponseException e) {
      logger.info("No pre-existing transfer job named {} found.", transferJobName);
    } catch (IOException e) {
      return new StepResult(
          StepStatus.STEP_RESULT_FAILURE_FATAL,
          new IllegalStateException("Failed to check for existing storage transfer job.", e));
    }
    // Get the service account in the control plane project used by the transfer service to
    // perform the actual data transfer. It's named for and scoped to the project.
    // Storage transfer service itself is free, so there should be no charges to the control
    // plane project. The usual egress charges will be made on the source bucket.
    // TODO(PF-888): understand what happens when the source bucket is requester pays. We don't
    //   support requester pays right now for controlled gcs buckets, but it might be set on a
    //   referenced bucket.
    final String transferServiceSAEmail =
        workingMap.get(ControlledResourceKeys.STORAGE_TRANSFER_SERVICE_SA_EMAIL, String.class);
    logger.debug("Storage Transfer Service SA: {}", transferServiceSAEmail);

    try {
      createTransferJob(
          sourceInputs,
          destinationInputs,
          transferJobName,
          controlPlaneProjectId,
          storageTransferService);
    } catch (IOException e) {
      return new StepResult(
          StepStatus.STEP_RESULT_FAILURE_FATAL,
          new IllegalStateException("Failed to copy bucket data", e));
    }

    return StepResult.getStepResultSuccess();
  }

  private void createTransferJob(
      BucketCloneInputs sourceInputs,
      BucketCloneInputs destinationInputs,
      String transferJobName,
      String controlPlaneProjectId,
      Storagetransfer storageTransferService)
      throws IOException {
    final TransferJob transferJobInput =
        new TransferJob()
            .setName(transferJobName)
            .setDescription(TRANSFER_JOB_DESCRIPTION)
            .setProjectId(controlPlaneProjectId)
            .setSchedule(createScheduleRunOnceNow())
            .setTransferSpec(
                createTransferSpec(sourceInputs.getBucketName(), destinationInputs.getBucketName()))
            .setStatus(ENABLED_STATUS);
    // Create the TransferJob for the associated schedule and spec in the correct project.
    final TransferJob transferJobOutput =
        storageTransferService.transferJobs().create(transferJobInput).execute();
    logger.debug("Created transfer job {}", transferJobOutput);
  }

  // Remove the Storage Transfer Job. Any data in the destination bucket will be deleted in the
  // previous step's undo method.
  @Override
  public StepResult undoStep(FlightContext flightContext) throws InterruptedException {
    return StorageTransferServiceUtils.deleteTransferJobStepImpl(flightContext);
  }

  private TransferSpec createTransferSpec(String sourceBucketName, String destinationBucketName) {
    return new TransferSpec()
        .setGcsDataSource(new GcsData().setBucketName(sourceBucketName))
        .setGcsDataSink(new GcsData().setBucketName(destinationBucketName))
        .setTransferOptions(
            new TransferOptions()
                .setDeleteObjectsFromSourceAfterTransfer(false)
                .setOverwriteObjectsAlreadyExistingInSink(false));
  }

  /**
   * Build a schedule to indicate that the job should run an operation immediately and not repeat
   * it.
   *
   * @return schedule object for the transfer job
   */
  private Schedule createScheduleRunOnceNow() {
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
}
