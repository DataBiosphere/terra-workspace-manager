package bio.terra.workspace.service.resource.controlled.flight.clone.bucket;

import static bio.terra.workspace.service.resource.controlled.flight.clone.bucket.StorageTransferServiceUtils.createTransferJob;
import static bio.terra.workspace.service.resource.controlled.flight.clone.bucket.StorageTransferServiceUtils.createTransferSpecForSignedUrl;

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

public class TransferSignedUrlsToGcsBucketStep implements Step {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(TransferSignedUrlsToGcsBucketStep.class);
  private final Storagetransfer storagetransfer;

  public TransferSignedUrlsToGcsBucketStep(Storagetransfer storageTransfer) {
    this.storagetransfer = storageTransfer;
  }

  @Override
  public StepResult doStep(FlightContext context) throws InterruptedException, RetryException {
    FlightMap workingMap = context.getWorkingMap();
    FlightUtils.validateRequiredEntries(
        workingMap,
        ControlledResourceKeys.CONTROL_PLANE_PROJECT_ID,
        ControlledResourceKeys.STORAGE_TRANSFER_SERVICE_SA_EMAIL);
    FlightUtils.validateRequiredEntries(
        context.getInputParameters(), ControlledResourceKeys.SIGNED_URL_LIST);

    String transferJobName =
        StorageTransferServiceUtils.createTransferJobName(context.getFlightId());
    workingMap.put(ControlledResourceKeys.STORAGE_TRANSFER_JOB_NAME, transferJobName);

    String controlPlaneProjectId =
        workingMap.get(ControlledResourceKeys.CONTROL_PLANE_PROJECT_ID, String.class);

    LOGGER.info(
        "Creating transfer job named {} in project {}", transferJobName, controlPlaneProjectId);

    String signedUrlList =
        context.getInputParameters().get(ControlledResourceKeys.SIGNED_URL_LIST, String.class);

    var destinationBucketName =
        context
            .getInputParameters()
            .get(ControlledResourceKeys.DESTINATION_BUCKET_NAME_FOR_SIGNED_URL_LIST, String.class);

    // Look up the transfer job by name. If it's found, it means we are restarting this step and
    // the job either has an operation in progress or completed (possibly failed).
    try {
      TransferJob existingTransferJob =
          storagetransfer.transferJobs().get(transferJobName, controlPlaneProjectId).execute();
      if (null != existingTransferJob) {
        LOGGER.info(
            "Transfer Job {} already exists. Nothing more for this step to do.", transferJobName);
        return StepResult.getStepResultSuccess();
      }
    } catch (GoogleJsonResponseException e) {
      LOGGER.info("No pre-existing transfer job named {} found.", transferJobName);
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
    var transferServiceSAEmail =
        workingMap.get(ControlledResourceKeys.STORAGE_TRANSFER_SERVICE_SA_EMAIL, String.class);
    LOGGER.debug("Storage Transfer Service SA: {}", transferServiceSAEmail);

    try {
      createTransferJob(
          storagetransfer,
          createTransferSpecForSignedUrl(signedUrlList, destinationBucketName),
          transferJobName,
          controlPlaneProjectId);
    } catch (IOException e) {
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY, e);
    }

    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    FlightMap workingMap = context.getWorkingMap();
    String transferJobName =
        workingMap.get(ControlledResourceKeys.STORAGE_TRANSFER_JOB_NAME, String.class);
    String controlPlaneProjectId =
        workingMap.get(ControlledResourceKeys.CONTROL_PLANE_PROJECT_ID, String.class);
    // A failure to delete will result in a DISMAL_FAILURE of the flight.
    return StorageTransferServiceUtils.deleteTransferJobStepImpl(
        context.getFlightId(), transferJobName, controlPlaneProjectId, storagetransfer);
  }
}
