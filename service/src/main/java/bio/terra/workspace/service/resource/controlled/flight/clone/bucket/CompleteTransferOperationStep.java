package bio.terra.workspace.service.resource.controlled.flight.clone.bucket;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.common.utils.FlightUtils;
import bio.terra.workspace.service.resource.controlled.exception.StorageTransferServiceTimeoutException;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import com.google.api.services.storagetransfer.v1.Storagetransfer;
import com.google.api.services.storagetransfer.v1.model.Operation;
import com.google.api.services.storagetransfer.v1.model.TransferJob;
import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Find the most recently started transfer operation for the flight's Storage Transfer job and wait
 * for it to complete.
 *
 * <p>Preconditions: Storage Transfer Service Job exists and an operation under it has been started.
 * The working map contains STORAGE_TRANSFER_JOB_NAME and CONTROL_PLANE_PROJECT_ID. The operation
 * created is assumed to be the most recent one started for this job.
 *
 * <p>Post conditions: Operation has completed or failed.
 */
public class CompleteTransferOperationStep implements Step {
  public static final Logger logger = LoggerFactory.getLogger(CompleteTransferOperationStep.class);
  private static final Duration JOBS_POLL_INTERVAL = Duration.ofSeconds(10);
  private static final Duration OPERATIONS_POLL_INTERVAL = Duration.ofSeconds(30);
  private static final int MAX_ATTEMPTS = 25;
  private final Storagetransfer storagetransfer;

  public CompleteTransferOperationStep(Storagetransfer storagetransfer) {
    this.storagetransfer = storagetransfer;
  }

  @Override
  public StepResult doStep(FlightContext flightContext)
      throws InterruptedException, RetryException {

    FlightUtils.validateRequiredEntries(
        flightContext.getWorkingMap(),
        ControlledResourceKeys.STORAGE_TRANSFER_JOB_NAME,
        ControlledResourceKeys.CONTROL_PLANE_PROJECT_ID);
    try {
      String transferJobName =
          flightContext
              .getWorkingMap()
              .get(ControlledResourceKeys.STORAGE_TRANSFER_JOB_NAME, String.class);
      String controlPlaneProjectId =
          flightContext
              .getWorkingMap()
              .get(ControlledResourceKeys.CONTROL_PLANE_PROJECT_ID, String.class);

      // Job is now submitted with its schedule. We need to poll the transfer operations API
      // for completion of the first transfer operation. The trick is going to be setting up a
      // polling interval that's appropriate for a wide range of bucket sizes. Everything from
      // milliseconds to hours. The transfer operation won't exist until it starts.
      String operationName = getLatestOperationName(transferJobName, controlPlaneProjectId);

      StepResult operationResult = getTransferOperationResult(transferJobName, operationName);

      if (StepStatus.STEP_RESULT_FAILURE_FATAL == operationResult.getStepStatus()) {
        return operationResult;
      }
    } catch (IOException e) {
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_FATAL, e);
    }

    return StepResult.getStepResultSuccess();
  }

  // Nothing to undo, as this step has no side effects
  @Override
  public StepResult undoStep(FlightContext flightContext) throws InterruptedException {
    return StepResult.getStepResultSuccess();
  }

  /**
   * Poll for completion of the named transfer operation and return the result.
   *
   * @param transferJobName - name of job owning the transfer operation
   * @param operationName - server-generated name of running operation
   * @return StepResult indicating success or failure
   * @throws IOException IOException
   * @throws InterruptedException InterruptedException
   */
  private StepResult getTransferOperationResult(String transferJobName, String operationName)
      throws IOException, InterruptedException {
    // Now that we have an operation name, we can poll the operations endpoint for completion
    // information.
    int attempts = 0;
    Operation operation;
    do {
      operation = storagetransfer.transferOperations().get(operationName).execute();
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
      return new StepResult(
          StepStatus.STEP_RESULT_FAILURE_FATAL,
          new StorageTransferServiceTimeoutException(message));
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
  // its server-generated name. Returns the most recently started operation's name. This is
  // reasonably safe, because the names are scoped to the transfer job.
  private String getLatestOperationName(String transferJobName, String projectId)
      throws InterruptedException, IOException {
    String operationName = null;
    for (int numAttempts = 0; numAttempts < MAX_ATTEMPTS; ++numAttempts) {
      final TransferJob getResponse =
          storagetransfer.transferJobs().get(transferJobName, projectId).execute();
      operationName = getResponse.getLatestOperationName();
      if (null != operationName) {
        break;
      } else {
        TimeUnit.MILLISECONDS.sleep(JOBS_POLL_INTERVAL.toMillis());
      }
    }
    if (null == operationName) {
      throw new StorageTransferServiceTimeoutException(
          "Exceeded max attempts to get transfer operation name");
    }

    logger.debug("Latest transfer operation name is {}", operationName);
    return operationName;
  }
}
