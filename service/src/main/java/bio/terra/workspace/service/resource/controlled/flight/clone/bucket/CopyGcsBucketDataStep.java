package bio.terra.workspace.service.resource.controlled.flight.clone.bucket;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.common.utils.FlightUtils;
import bio.terra.workspace.generated.model.ApiClonedControlledGcpGcsBucket;
import bio.terra.workspace.service.resource.model.CloningInstructions;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import com.google.api.client.util.Strings;
import com.google.api.services.storagetransfer.v1.Storagetransfer;
import com.google.api.services.storagetransfer.v1.model.Date;
import com.google.api.services.storagetransfer.v1.model.GcsData;
import com.google.api.services.storagetransfer.v1.model.Operation;
import com.google.api.services.storagetransfer.v1.model.Schedule;
import com.google.api.services.storagetransfer.v1.model.TransferJob;
import com.google.api.services.storagetransfer.v1.model.TransferOptions;
import com.google.api.services.storagetransfer.v1.model.TransferSpec;
import java.io.IOException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;

public final class CopyGcsBucketDataStep implements Step {

  private static final Duration JOBS_POLL_INTERVAL = Duration.ofSeconds(10);
  private static final Duration OPERATIONS_POLL_INTERVAL = Duration.ofSeconds(30);
  private static final int MAX_ATTEMPTS = 25;
  private static final Logger logger = LoggerFactory.getLogger(CopyGcsBucketDataStep.class);
  private static final String APPLICATION_NAME = "terra-workspace-manager";
  private static final String ENABLED_STATUS = "ENABLED";

  public CopyGcsBucketDataStep() {}

  // See https://cloud.google.com/storage-transfer/docs/reference/rest/v1/transferJobs/create
  // (somewhat dated) and
  // https://cloud.google.com/storage-transfer/docs/create-manage-transfer-program
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

    // Get source & destination bucket input values
    final BucketCloneInputs sourceInputs =
        workingMap.get(ControlledResourceKeys.SOURCE_BUCKET_CLONE_INPUTS, BucketCloneInputs.class);
    final BucketCloneInputs destinationInputs =
        workingMap.get(
            ControlledResourceKeys.DESTINATION_BUCKET_CLONE_INPUTS, BucketCloneInputs.class);
    logger.info(
        "Starting data copy from source bucket \n\t{}\nto destination\n\t{}",
        sourceInputs,
        destinationInputs);

    final String transferJobName = createTransferJobName();
    final String controlPlaneProjectId =
        workingMap.get(ControlledResourceKeys.CONTROL_PLANE_PROJECT_ID, String.class);
    logger.info(
        "Creating transfer job named {} in project {}", transferJobName, controlPlaneProjectId);

    try {
      final Storagetransfer storageTransferService =
          StorageTransferServiceUtils.createStorageTransferService();

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

      final TransferJob transferJobInput =
          new TransferJob()
              .setName(transferJobName)
              .setDescription("Terra Workspace Manager Clone GCS Bucket")
              .setProjectId(controlPlaneProjectId)
              .setSchedule(createScheduleRunOnceNow())
              .setTransferSpec(
                  createTransferSpec(
                      sourceInputs.getBucketName(), destinationInputs.getBucketName()))
              .setStatus(ENABLED_STATUS);
      // Create the TransferJob for the associated schedule and spec in the correct project.
      final TransferJob transferJobOutput =
          storageTransferService.transferJobs().create(transferJobInput).execute();
      logger.debug("Created transfer job {}", transferJobOutput);
      // Job is now submitted with its schedule. We need to poll the transfer operations API
      // for completion of the first transfer operation. The trick is going to be setting up a
      // polling interval that's appropriate for a wide range of bucket sizes. Everything from
      // millisecond
      // to hours. The transfer operation won't exist until it starts.
      final String operationName =
          getLatestOperationName(storageTransferService, transferJobName, controlPlaneProjectId);

      final StepResult operationResult =
          getTransferOperationResult(storageTransferService, transferJobName, operationName);

      if (StepStatus.STEP_RESULT_FAILURE_FATAL == operationResult.getStepStatus()) {
        return operationResult;
      }
      // Currently there is no delete endpoint for transfer jobs, so all of the completed clone jobs
      // will clutter the console in the main control plane project.
      // https://cloud.google.com/storage-transfer/docs/reference/rest
    } catch (IOException e) {
      return new StepResult(
          StepStatus.STEP_RESULT_FAILURE_FATAL,
          new IllegalStateException("Failed to copy bucket data", e));
    }

    final ApiClonedControlledGcpGcsBucket apiBucketResult =
        flightContext
            .getWorkingMap()
            .get(
                ControlledResourceKeys.CLONE_DEFINITION_RESULT,
                ApiClonedControlledGcpGcsBucket.class);
    FlightUtils.setResponse(flightContext, apiBucketResult, HttpStatus.OK);
    return StepResult.getStepResultSuccess();
  }

  // Since we are billing users for the transfers, we don't want to throw away data from a partial
  // success, especially for large bucket transfers.
  @Override
  public StepResult undoStep(FlightContext flightContext) throws InterruptedException {
    return StepResult.getStepResultSuccess();
  }

  /**
   * Poll for completion of the named transfer operation and return the result.
   *
   * @param storageTransferService - svc to perform the transfer
   * @param transferJobName - name of job owning the transfer operation
   * @param operationName - server-generated name of running operation
   * @return StepResult indicating success or failure
   * @throws IOException
   * @throws InterruptedException
   */
  private StepResult getTransferOperationResult(
      Storagetransfer storageTransferService, String transferJobName, String operationName)
      throws IOException, InterruptedException {
    // Now that we have an operation name, we can poll the operations endpoint for completion
    // information.
    int attempts = 0;
    Operation operation;
    do {
      operation = storageTransferService.transferOperations().get(operationName).execute();
      if (operation == null) {
        throw new RuntimeException(
            String.format("Failed to get transfer operation with name %s", operationName));
      } else if (operation.getDone() != null && operation.getDone()) {
        break;
      } else {
        // operation is not started or is in progress
        TimeUnit.MILLISECONDS.sleep(OPERATIONS_POLL_INTERVAL.toMillis());
        attempts++;
        logger.debug("Attempted to get transfer operation {} {} times", operationName, attempts);
      }
    } while (attempts < MAX_ATTEMPTS);
    if (MAX_ATTEMPTS <= attempts) {
      final String message = "Timed out waiting for operation result.";
      logger.info(message);
      final RuntimeException e = new RuntimeException(message);
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY, e);
    }
    logger.info("Operation {} in transfer job {} has completed", operationName, transferJobName);
    // Inspect the completed operation for success
    if (operation.getError() != null) {
      logger.warn("Error in transfer operation {}: {}", operationName, operation.getError());
      final RuntimeException e =
          new RuntimeException("Failed transfer with error " + operation.getError().toString());
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_FATAL, e);
    } else {
      logger.debug("Completed operation metadata: {}", operation.getMetadata());
      return StepResult.getStepResultSuccess();
    }
  }

  // First, we poll the transfer jobs endpoint until an operation has started so that we can get
  // its server-generated name. Returns the most recently started operation's name.
  private String getLatestOperationName(
      Storagetransfer storageTransferService, String transferJobName, String projectId)
      throws InterruptedException, IOException {
    String operationName = null;
    for(int numAttempts = 0; numAttempts < MAX_ATTEMPTS; ++numAttempts) {
      final TransferJob getResponse =
          storageTransferService.transferJobs().get(transferJobName, projectId).execute();
      operationName = getResponse.getLatestOperationName();
      if (null != operationName) {
        break;
      } else {
        TimeUnit.MILLISECONDS.sleep(JOBS_POLL_INTERVAL.toMillis());
      }
    }
    if (null == operationName) {
      throw new RuntimeException("Exceeded max attempts to get transfer operation name");
    }

    logger.debug("Latest transfer operation name is {}", operationName);
    return operationName;
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

  private String createTransferJobName() {
    return "transferJobs/wsm-" + UUID.randomUUID().toString();
  }
}
