package bio.terra.workspace.service.resource.controlled.flight.clone.bucket;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.service.resource.controlled.ControlledGcsBucketResource;
import bio.terra.workspace.service.resource.model.CloningInstructions;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import com.google.api.client.googleapis.util.Utils;
import com.google.api.services.storagetransfer.v1.Storagetransfer;
import com.google.api.services.storagetransfer.v1.Storagetransfer.TransferOperations;
import com.google.api.services.storagetransfer.v1.StoragetransferScopes;
import com.google.api.services.storagetransfer.v1.model.Date;
import com.google.api.services.storagetransfer.v1.model.GcsData;
import com.google.api.services.storagetransfer.v1.model.Operation;
import com.google.api.services.storagetransfer.v1.model.Schedule;
import com.google.api.services.storagetransfer.v1.model.TimeOfDay;
import com.google.api.services.storagetransfer.v1.model.TransferJob;
import com.google.api.services.storagetransfer.v1.model.TransferOptions;
import com.google.api.services.storagetransfer.v1.model.TransferSpec;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class CopyGcsBucketDataStep implements Step {

  public static final String ROOT_PATH = "/";
  public static final String ENABLED_STATUS = "ENABLED";
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
    final CloningInstructions cloningInstructions =
        flightContext
            .getWorkingMap()
            .get(ControlledResourceKeys.CLONING_INSTRUCTIONS, CloningInstructions.class);
    if (cloningInstructions != CloningInstructions.COPY_RESOURCE) {
      return StepResult.getStepResultSuccess();
    }
    final String sourceBucketName = sourceBucket.getBucketName();
    // Not to be confused with the destination bucket name in the input parameters.
    final String destinationBucketName =
        flightContext
            .getWorkingMap()
            .get(ControlledResourceKeys.DESTINATION_BUCKET_NAME, String.class);
    try {
      final Storagetransfer storageTransferService = createStorageTransferService();
      final TransferSpec transferSpec =
          new TransferSpec()
              .setGcsDataSource(new GcsData().setBucketName(sourceBucketName).setPath(ROOT_PATH))
              .setGcsDataSink(new GcsData().setBucketName(destinationBucketName).setPath(ROOT_PATH))
              .setTransferOptions(
                  new TransferOptions()
                      .setDeleteObjectsFromSourceAfterTransfer(false)
                      .setOverwriteObjectsAlreadyExistingInSink(true));
      final String jobName = createTransferJobName();
      final String projectId = "terra-kernel-k8s"; // TODO: pull from config
      final TransferJob transferJobInput =
          new TransferJob()
              .setName(jobName)
              .setDescription("Workspace Manager Clone GCS Bucket")
              .setProjectId(projectId)
              .setSchedule(createScheduleRunOnceNow())
              .setTransferSpec(transferSpec)
              .setStatus(ENABLED_STATUS);

      Storagetransfer.TransferJobs.Create createRequest =
          storageTransferService.transferJobs().create(transferJobInput);

      TransferJob transferJobOutput = createRequest.execute();
      // Job is now submitted with its schedule. We need to Poll for completion of the first
      // transfer.
      boolean isDone = false;
      int triesRemaining = 10;
      do {
        final Storagetransfer.TransferJobs.Get getRequest =
            storageTransferService.transferJobs().get(jobName, projectId);
        final TransferJob getResponse = getRequest.execute();
        final String latestOperationName = getResponse.getLatestOperationName();
        if (latestOperationName != null) {
          final TransferOperations.Get latestOperationGet =
              storageTransferService.transferOperations().get(latestOperationName);
          final Operation operation = latestOperationGet.execute();
          if (operation.getDone()) {
            isDone = true;
          }
        } else {
          TimeUnit.SECONDS.sleep(15);
        }
        triesRemaining--;
      } while (!isDone && triesRemaining > 0);
    } catch (IOException | GeneralSecurityException e) {
      throw new IllegalStateException("Failed to copy bucket data", e);
    }

    return StepResult.getStepResultSuccess();
  }

  private Schedule createScheduleRunOnceNow() {
    final OffsetDateTime now = OffsetDateTime.now();
    final Date runDate =
        new Date().setYear(now.getYear()).setMonth(now.getMonthValue()).setDay(now.getDayOfMonth());
    final TimeOfDay runTimeOfDay =
        new TimeOfDay()
            .setHours(now.getHour())
            .setMinutes(now.getMinute())
            .setSeconds(now.getSecond()); // must be in future to only run once
    return new Schedule()
        .setScheduleStartDate(runDate)
        .setScheduleEndDate(runDate)
        .setStartTimeOfDay(runTimeOfDay)
        .setEndTimeOfDay(runTimeOfDay);
  }

  private String createTransferJobName() {
    return "transferJobs/wsm-" + UUID.randomUUID().toString();
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) throws InterruptedException {
    return StepResult.getStepResultSuccess();
  }

  private Storagetransfer createStorageTransferService()
      throws IOException, GeneralSecurityException {
    GoogleCredentials credential = GoogleCredentials.getApplicationDefault();
    if (credential.createScopedRequired()) {
      credential = credential.createScoped(StoragetransferScopes.all());
    }
    if (credential.createScopedRequired()) {
      credential =
          credential.createScoped(
              Collections.singletonList("https://www.googleapis.com/auth/cloud-platform"));
    }
    return new Storagetransfer.Builder(
            Utils.getDefaultTransport(),
            Utils.getDefaultJsonFactory(),
            new HttpCredentialsAdapter(credential))
        .build();
  }
}
