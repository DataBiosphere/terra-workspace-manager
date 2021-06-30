package bio.terra.workspace.service.resource.controlled.flight.clone.bucket;

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
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;

public class CopyGcsBucketDataStep implements Step {

  private static final String APPLICATION_NAME = "terra-workspace-manager";
  private static final Duration FUTURE_DELAY = Duration.ofSeconds(5);
  private static final Duration JOBS_POLL_INTERVAL = Duration.ofSeconds(10);
  private static final Duration OPERATIONS_POLL_INTERVAL = Duration.ofSeconds(30);
  private static final ImmutableSet<String> DESTINATION_BUCKET_ROLE_NAMES =
      ImmutableSet.of("roles/storage.legacyBucketWriter");
  private static final ImmutableSet<String> SOURCE_BUCKET_ROLE_NAMES =
      ImmutableSet.of("roles/storage.objectViewer", "roles/storage.legacyBucketReader");
  private static final int MAX_ATTEMPTS = 25;
  private static final Logger logger = LoggerFactory.getLogger(CopyGcsBucketDataStep.class);
  private static final String ENABLED_STATUS = "ENABLED";

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

  private static class BucketInputs {
    private final UUID workspaceId;
    private final String projectId;
    private final String bucketName;
    private final Set<Role> roles;

    public BucketInputs(UUID workspaceId, String projectId, String bucketName, Set<Role> roles) {
      this.workspaceId = workspaceId;
      this.projectId = projectId;
      this.bucketName = bucketName;
      this.roles = roles;
    }

    public UUID getWorkspaceId() {
      return workspaceId;
    }

    public String getProjectId() {
      return projectId;
    }

    public String getBucketName() {
      return bucketName;
    }

    public Set<Role> getRoles() {
      return roles;
    }

    @Override
    public String toString() {
      return "BucketInputs{"
          + "workspaceId="
          + workspaceId
          + ", projectId='"
          + projectId
          + '\''
          + ", bucketName='"
          + bucketName
          + '\''
          + ", roles="
          + roles
          + '}';
    }
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
    // Get source & destination bucket input values
    final BucketInputs sourceInputs = getSourceInputs();
    final BucketInputs destinationInputs = getDestinationInputs(flightContext);

    logger.info(
        "Starting data copy from source bucket \n\t{}\nto destination\n\t{}",
        sourceInputs,
        destinationInputs);
    final String transferJobName = createTransferJobName();
    final String controlPlaneProjectId =
        Optional.ofNullable(ServiceOptions.getDefaultProjectId())
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "Could not determine default GCP control plane project ID."));
    logger.info(
        "Creating transfer job named {} in project {}", transferJobName, controlPlaneProjectId);

    try {
      final Storagetransfer storageTransferService = createStorageTransferService();

      // Get the service account in the control plane project used by the transfer service to
      // perform the actual data transfer. It's named for and scoped to the project.
      final GoogleServiceAccount transferServiceSA =
          storageTransferService.googleServiceAccounts().get(controlPlaneProjectId).execute();
      logger.debug("Storage Transfer Service SA: {}", transferServiceSA.getAccountEmail());
      final String transferServiceSAEmail = transferServiceSA.getAccountEmail();
      // Set source bucket permissions
      addBucketRoles(sourceInputs, transferServiceSAEmail);

      // Set destination roles
      addBucketRoles(destinationInputs, transferServiceSAEmail);

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
      // polling
      // interval that's appropriate for a wide range of bucket sizes. Everything from millisecond
      // to hours. The transfer operation won't exist until it starts.
      final String operationName =
          getLatestOperationName(storageTransferService, transferJobName, controlPlaneProjectId);

      final StepResult operationResult =
          getTransferOperationResult(storageTransferService, transferJobName, operationName);
      removeBucketRoles(sourceInputs, transferServiceSAEmail);
      removeBucketRoles(destinationInputs, transferServiceSAEmail);

      if (StepStatus.STEP_RESULT_FAILURE_FATAL == operationResult.getStepStatus()) {
        return operationResult;
      }

      // Currently there is no delete endpoint for transfer jobs, so all of the completed clone jobs
      // will clutter the console in the main control plane project.
      // https://cloud.google.com/storage-transfer/docs/reference/rest
    } catch (IOException | GeneralSecurityException e) {
      throw new IllegalStateException("Failed to copy bucket data", e);
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

  private BucketInputs getSourceInputs() {
    final String sourceProjectId =
        workspaceService.getRequiredGcpProject(sourceBucket.getWorkspaceId());
    final String sourceBucketName = sourceBucket.getBucketName();
    final Set<Role> roles =
        SOURCE_BUCKET_ROLE_NAMES.stream().map(Role::of).collect(Collectors.toSet());
    return new BucketInputs(
        sourceBucket.getWorkspaceId(), sourceProjectId, sourceBucketName, roles);
  }

  private BucketInputs getDestinationInputs(FlightContext flightContext) {
    // Not to be confused with the destination bucket name in the input parameters, which was
    // processed
    // in a previous step. This is the effective destination bucket name that was either
    // user-supplied
    // or generated randomly.
    final UUID workspaceId =
        flightContext
            .getInputParameters()
            .get(ControlledResourceKeys.DESTINATION_WORKSPACE_ID, UUID.class);
    final String projectId = workspaceService.getRequiredGcpProject(workspaceId);
    final String bucketName =
        flightContext
            .getWorkingMap()
            .get(ControlledResourceKeys.DESTINATION_BUCKET_NAME, String.class);
    final Set<Role> roles =
        DESTINATION_BUCKET_ROLE_NAMES.stream().map(Role::of).collect(Collectors.toSet());
    return new BucketInputs(workspaceId, projectId, bucketName, roles);
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
    logger.info("Operation {} in transfer job {} has completed", operationName, transferJobName);
    // Inspect the completed operation for success
    if (operation.getError() != null) {
      logger.warn("Error in transfer operation {}: {}", operationName, operation.getError());
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_FATAL); // don't retry for now
    } else {
      logger.debug("Completed operation metadata: {}", operation.getMetadata());
      return StepResult.getStepResultSuccess();
    }
  }

  private void addBucketRoles(BucketInputs inputs, String transferServiceSAEmail) {
    addOrRemoveBucketIdentities(BucketPolicyIdentityOperation.ADD, inputs, transferServiceSAEmail);
  }

  private void removeBucketRoles(BucketInputs inputs, String transferServiceSAEmail) {
    addOrRemoveBucketIdentities(
        BucketPolicyIdentityOperation.REMOVE, inputs, transferServiceSAEmail);
  }

  private enum BucketPolicyIdentityOperation {
    ADD,
    REMOVE
  }

  /**
   * Add or remove roles for an Identity
   *
   * @param operation - flag for add or remove
   * @param inputs - source or destination input object
   * @param transferServiceSAEmail - STS SA email address
   */
  private void addOrRemoveBucketIdentities(
      BucketPolicyIdentityOperation operation, BucketInputs inputs, String transferServiceSAEmail) {
    final StorageCow storageCow = crlService.createStorageCow(inputs.getProjectId());
    final Identity saIdentity = Identity.serviceAccount(transferServiceSAEmail);

    final Policy.Builder policyBuilder =
        storageCow.getIamPolicy(inputs.getBucketName()).toBuilder();
    for (Role role : inputs.getRoles()) {
      switch (operation) {
        case ADD:
          policyBuilder.addIdentity(role, saIdentity);
          break;
        case REMOVE:
          policyBuilder.removeIdentity(role, saIdentity);
          break;
      }
    }
    storageCow.setIamPolicy(inputs.getBucketName(), policyBuilder.build());
  }

  // First, we poll the transfer jobs endpoint until an operation has started so that we can get
  // its server-generated name.
  private String getLatestOperationName(
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
   * it. This isn't well-supported in the UI, but there are hints in the documentation. The most
   * frustrating feature of the interface is that if a start date & time are in "the past", the run
   * won't initiate until the next iteration (e.g. in 24 hours, assuming that's not past the end
   * time). Which means we need to apply a fudge factor to make sure the time is a little bit in the
   * future. Note that stepping through the code in the debugger too slowly can leave you with a
   * stale timestamp and cause the job not to run.
   *
   * @return schedule object for the transfer job
   */
  private Schedule createScheduleRunOnceNow() {
    final OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    final Date runDate =
        new Date().setYear(now.getYear()).setMonth(now.getMonthValue()).setDay(now.getDayOfMonth());
    // "If `schedule_end_date` and schedule_start_date are the same and
    // in the future relative to UTC, the transfer is executed only one time."
    return new Schedule().setScheduleStartDate(runDate).setScheduleEndDate(runDate);
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
        .setApplicationName(APPLICATION_NAME)
        .build();
  }
}
