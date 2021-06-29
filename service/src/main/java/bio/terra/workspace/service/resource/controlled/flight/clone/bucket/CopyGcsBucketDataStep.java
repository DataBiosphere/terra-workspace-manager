package bio.terra.workspace.service.resource.controlled.flight.clone.bucket;

import bio.terra.cloudres.google.iam.IamCow;
import bio.terra.cloudres.google.storage.StorageCow;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.common.utils.FlightUtils;
import bio.terra.workspace.generated.model.ApiClonedControlledGcpGcsBucket;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.resource.controlled.ControlledGcsBucketResource;
import bio.terra.workspace.service.resource.model.CloningInstructions;
import bio.terra.workspace.service.workspace.WorkspaceService;
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
import com.google.cloud.Identity;
import com.google.cloud.Policy;
import com.google.cloud.Role;
import com.google.cloud.ServiceOptions;
import com.google.common.collect.ImmutableSet;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;

public class CopyGcsBucketDataStep implements Step {

  private static final Logger logger = LoggerFactory.getLogger(CopyGcsBucketDataStep.class);
  private static final String ENABLED_STATUS = "ENABLED";
  private static final Duration FUTURE_DELAY = Duration.ofSeconds(5);
  private static final Duration JOBS_POLL_INTERVAL = Duration.ofSeconds(10);
  private static final Duration OPERATIONS_POLL_INTERVAL = Duration.ofSeconds(30);
  private static final int MAX_ATTEMPTS = 100;

  private final ControlledGcsBucketResource sourceBucket;
  private final CrlService crlService;
  private final WorkspaceService workspaceService;

  public CopyGcsBucketDataStep(
      ControlledGcsBucketResource sourceBucket,
      CrlService crlService,
      WorkspaceService workspaceService) {
    this.sourceBucket = sourceBucket;
    this.crlService = crlService;
    this.workspaceService = workspaceService;
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
    final IamCow iamCow = crlService.getIamCow();

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
      setSourceBucketRoles(sourceBucketName, transferServiceSA.getAccountEmail());

      // Set destination permissions
      final UUID destinationWorkspaceId =
          flightContext
              .getInputParameters()
              .get(ControlledResourceKeys.DESTINATION_WORKSPACE_ID, UUID.class);
      setDestinationBucketRoles(
          destinationWorkspaceId, destinationBucketName, transferServiceSA.getAccountEmail());

      final TransferJob transferJobInput =
          new TransferJob()
              .setName(transferJobName)
              .setDescription("Workspace Manager Clone GCS Bucket")
              .setProjectId(projectId)
              .setSchedule(createScheduleRunOnceNow())
              .setTransferSpec(createBulkTransferSpec(sourceBucketName, destinationBucketName))
              //                  createBulkTransferSpec(
              //                      "jaycarlton-test-source-1", "jaycarlton-test-destination-1"))
              .setStatus(ENABLED_STATUS);
      // Create the TransferJob for the associated schedule and spec in the correct project.
      final TransferJob transferJobOutput =
          storageTransferService.transferJobs().create(transferJobInput).execute();

      // Job is now submitted with its schedule. We need to poll the transfer operations API
      // for completion of the first transfer operation. The trick is going to be setting up a
      // polling
      // interval that's appropriate for a wide range of bucket sizes. Everything from millisecond
      // to hours. The transfer operation won't exist until it starts.
      final String operationName =
          pollForOperationName(storageTransferService, transferJobName, projectId);

      // Now that we have an operation name, we can poll the operations endpoint for completion
      // information.
      int attempts = 0;
      Operation operation;
      do {
        operation = storageTransferService.transferOperations().get(operationName).execute();
        if (operation == null) {
          throw new RuntimeException(
              String.format("Failed to get transfer operation with name %s", operationName));
        } else if (operation.getDone()) {
          break;
        }
        TimeUnit.MILLISECONDS.sleep(OPERATIONS_POLL_INTERVAL.toMillis());
        attempts++;
        logger.info("Attempted to get transfer operation {} {} times", operationName, attempts);
      } while (attempts < MAX_ATTEMPTS);
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
    // TODO: delete the completed transfer job, as it will never run again
    final ApiClonedControlledGcpGcsBucket apiBucketResult =
        flightContext
            .getWorkingMap()
            .get(
                ControlledResourceKeys.CLONE_DEFINITION_RESULT,
                ApiClonedControlledGcpGcsBucket.class);
    FlightUtils.setResponse(flightContext, apiBucketResult, HttpStatus.OK);
    return StepResult.getStepResultSuccess();
  }

  private void setSourceBucketRoles(String sourceBucketName, String transferServiceSAEmail) {
    setBucketRoles(
        sourceBucket.getWorkspaceId(),
        sourceBucketName,
        transferServiceSAEmail,
        ImmutableSet.of("roles/storage.objectViewer", "roles/storage.legacyBucketReader"));
  }

  private void setDestinationBucketRoles(
      UUID destinationWorkspaceId, String bucketName, String transferServiceSAEmail) {
    setBucketRoles(
        destinationWorkspaceId,
        bucketName,
        transferServiceSAEmail,
        ImmutableSet.of("roles/storage.legacyBucketWriter"));
  }

  private void setBucketRoles(
      UUID workspaceId, String bucketName, String transferServiceSAEmail, Set<String> roles) {
    final String projectId = workspaceService.getRequiredGcpProject(workspaceId);
    final StorageCow storageCow = crlService.createStorageCow(projectId);
    final Identity saIdentity = Identity.serviceAccount(transferServiceSAEmail);
    final Policy.Builder policyBuilder = storageCow.getIamPolicy(bucketName).toBuilder();
    roles.forEach(s -> policyBuilder.addIdentity(Role.of(s), saIdentity));
    storageCow.setIamPolicy(bucketName, policyBuilder.build());
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
        .setGcsDataSource(new GcsData().setBucketName(sourceBucketName))
        .setGcsDataSink(new GcsData().setBucketName(destinationBucketName))
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
        .setApplicationName("wsm-fixme") // FIXME
        .build();
  }
}
