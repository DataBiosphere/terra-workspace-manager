package bio.terra.workspace.service.resource.controlled.flight.clone.bucket;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.service.resource.controlled.ControlledGcsBucketResource;
import bio.terra.workspace.service.resource.model.CloningInstructions;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import com.google.api.client.googleapis.util.Utils;
import com.google.api.services.storagetransfer.v1.Storagetransfer;
import com.google.api.services.storagetransfer.v1.StoragetransferScopes;
import com.google.api.services.storagetransfer.v1.model.Date;
import com.google.api.services.storagetransfer.v1.model.GcsData;
import com.google.api.services.storagetransfer.v1.model.GoogleServiceAccount;
import com.google.api.services.storagetransfer.v1.model.Operation;
import com.google.api.services.storagetransfer.v1.model.Schedule;
import com.google.api.services.storagetransfer.v1.model.TimeOfDay;
import com.google.api.services.storagetransfer.v1.model.TransferJob;
import com.google.api.services.storagetransfer.v1.model.TransferOptions;
import com.google.api.services.storagetransfer.v1.model.TransferSpec;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.ServiceOptions;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CopyGcsBucketDataStep implements Step {

  private static final Logger logger = LoggerFactory.getLogger(CopyGcsBucketDataStep.class);
  private static final String ROOT_PATH = "/";
  private static final String ENABLED_STATUS = "ENABLED";
  private static final Duration FUTURE_DELAY = Duration.ofSeconds(5);
  private static final Duration JOBS_POLL_INTERVAL = Duration.ofSeconds(10);
  private static final Duration OPERATIONS_POLL_INTERVAL = Duration.ofSeconds(30);
  private static final int MAX_ATTEMPTS = 100;

  private final ControlledGcsBucketResource sourceBucket;

  public CopyGcsBucketDataStep(ControlledGcsBucketResource sourceBucket) {
    this.sourceBucket = sourceBucket;
  }

  // See https://cloud.google.com/storage-transfer/docs/reference/rest/v1/transferJobs/create
  // (somewhat dated) and
  // https://cloud.google.com/storage-transfer/docs/create-manage-transfer-program
  @Override
  public StepResult doStep(FlightContext flightContext)
      throws InterruptedException, RetryException {
    final CloningInstructions effectiveCloningInstructions =
        flightContext
            .getWorkingMap()
            .get(ControlledResourceKeys.CLONING_INSTRUCTIONS, CloningInstructions.class);
    // This step is only run for full resource clones
    if (effectiveCloningInstructions != CloningInstructions.COPY_RESOURCE) {
      return StepResult.getStepResultSuccess();
    }
    final String sourceBucketName = sourceBucket.getBucketName();
    // Not to be confused with the destination bucket name in the input parameters.
    final String destinationBucketName =
        flightContext
            .getWorkingMap()
            .get(ControlledResourceKeys.DESTINATION_BUCKET_NAME, String.class);
    logger.info("Starting data copy from bucket {} to {}", sourceBucketName, destinationBucketName);
    try {
      final Storagetransfer storageTransferService = createStorageTransferService();

      final String transferJobName = createTransferJobName();
      final String projectId =
          Optional.ofNullable(ServiceOptions.getDefaultProjectId())
              .orElseThrow(
                  () -> new IllegalStateException("Could not determine default GCP project ID."));
      logger.info("Creating transfer job named {} in project {}", transferJobName, projectId);
      final GoogleServiceAccount transferServiceSA =
          storageTransferService.googleServiceAccounts().get(projectId).execute();
      logger.info("STS SA: {}", transferServiceSA.getAccountEmail());

      // Set source bucket permissions

      final TransferJob transferJobInput =
          new TransferJob()
              .setName(transferJobName)
              .setDescription("Workspace Manager Clone GCS Bucket")
              .setProjectId(projectId)
              .setSchedule(createScheduleRunOnceNow())
              .setTransferSpec(createBulkTransferSpec(sourceBucketName, destinationBucketName))
              .setStatus(ENABLED_STATUS);
      // Create the TransferJob for the associated schedule and spec in the correct project.
      final TransferJob transferJobOutput =
          storageTransferService.transferJobs().create(transferJobInput).execute();

      // Job is now submitted with its schedule. We need to poll the transfer operations API
      // for completion of the first transfer operation. The trick is going to be setting up a
      // polling
      // interval that's appropriate for a wide range of bucket sizes. Everything from millisecond
      // to hours.

      final String operationName =
          pollForOperationName(storageTransferService, transferJobName, projectId);

      // Now that we have an operation name, we can poll the operations endpoint for completion
      // information.
      int attempts = 0;
      Operation operation;
      do {
        operation = storageTransferService.transferOperations().get(operationName).execute();
        TimeUnit.MILLISECONDS.sleep(OPERATIONS_POLL_INTERVAL.toMillis());
        attempts++;
      } while (!operation.getDone() && attempts < MAX_ATTEMPTS);
      logger.info("Operation {} in transfer job {} has completed", operationName, transferJobName);
      // Inspect the completed operation for success
      if (operation.getError() != null) {
        logger.warn("Error in transfer operation {}: {}", operationName, operation.getError());
        return new StepResult(StepStatus.STEP_RESULT_FAILURE_FATAL); // don't retry for now
      } else {
        // response should be filled in
        final Map<String, Object> responseMap = operation.getResponse();
        final Map<String, Object> metadata = operation.getMetadata();
      }
    } catch (IOException | GeneralSecurityException e) {
      throw new IllegalStateException("Failed to copy bucket data", e);
    }

    return StepResult.getStepResultSuccess();
  }

  // First, we poll the transfer jobs endpoint until an operation has started so that we can get
  // its server-generated name.
  private String pollForOperationName(
      Storagetransfer storageTransferService, String transferJobName, String projectId)
      throws InterruptedException, IOException {
    int attempts = 0;
    String operationName;
    do {
      TimeUnit.MILLISECONDS.sleep(JOBS_POLL_INTERVAL.toMillis());
      final TransferJob getResponse =
          storageTransferService.transferJobs().get(transferJobName, projectId).execute();
      operationName = getResponse.getLatestOperationName();
      attempts++;
      if (attempts > MAX_ATTEMPTS) {
        throw new RuntimeException("Exceeded max attempts to get transfer operation name");
      }
    } while (operationName == null);
    logger.info("Latest transfer operation name is {}", operationName);
    return operationName;
  }

  private TransferSpec createBulkTransferSpec(
      String sourceBucketName, String destinationBucketName) {
    return new TransferSpec()
        .setGcsDataSource(new GcsData().setBucketName(sourceBucketName).setPath(ROOT_PATH))
        .setGcsDataSink(new GcsData().setBucketName(destinationBucketName).setPath(ROOT_PATH))
        .setTransferOptions(
            new TransferOptions()
                .setDeleteObjectsFromSourceAfterTransfer(false)
                .setOverwriteObjectsAlreadyExistingInSink(false));
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) throws InterruptedException {
    return StepResult.getStepResultSuccess();
  }

  private Schedule createScheduleRunOnceNow() {
    final OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC).plus(FUTURE_DELAY);
    final Date runDate =
        new Date().setYear(now.getYear()).setMonth(now.getMonthValue()).setDay(now.getDayOfMonth());
    // Use a time a few seconds in the future to ensure the transfer doesn't have a
    // start date in the past.
    final TimeOfDay runTimeOfDay =
        new TimeOfDay()
            .setHours(now.getHour())
            .setMinutes(now.getMinute())
            .setSeconds(now.getSecond());
    // " If `schedule_end_date` and schedule_start_date are the same and
    // in the future relative to UTC, the transfer is executed only one time."
    return new Schedule()
        .setScheduleStartDate(runDate)
        .setScheduleEndDate(runDate)
        .setStartTimeOfDay(runTimeOfDay)
        .setEndTimeOfDay(runTimeOfDay);
  }

  private String createTransferJobName() {
    return "transferJobs/wsm-" + UUID.randomUUID().toString();
  }

  private Storagetransfer createStorageTransferService()
      throws IOException, GeneralSecurityException {
    GoogleCredentials credential = GoogleCredentials.getApplicationDefault();
    if (credential.createScopedRequired()) {
      credential = credential.createScoped(StoragetransferScopes.all());
    }

    return new Storagetransfer.Builder(
            Utils.getDefaultTransport(),
            Utils.getDefaultJsonFactory(),
            new HttpCredentialsAdapter(credential))
        .build();
  }
}
