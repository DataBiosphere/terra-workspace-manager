package bio.terra.workspace.service.workspace.flight;

import static bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.GCP_PROJECT_ID;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.common.utils.GcpUtils;
import bio.terra.workspace.common.utils.RetryUtils;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Do not complete the cloud context creation until the project permissions have propagated. */
public class WaitForProjectPermissionsStep implements Step {
  private final Logger logger = LoggerFactory.getLogger(WaitForProjectPermissionsStep.class);

  /**
   * CRL doesn't support list-bucket. For now we call GCP directly.
   *
   * @param userRequest user creating the cloud contxt
   * @param gcpProjectId project that got created
   * @return GCS Storage object
   */
  private Storage getStorage(AuthenticatedUserRequest userRequest, String gcpProjectId) {
    StorageOptions.Builder optionsBuilder = StorageOptions.newBuilder();
    optionsBuilder.setCredentials(GcpUtils.getGoogleCredentialsFromUserRequest(userRequest));
    optionsBuilder.setProjectId(gcpProjectId);
    return optionsBuilder.build().getService();
  }

  /**
   * Method to be used for testing that project permissions have propagated. We are assuming that if
   * one permission is working, they all have been sync'd. As far as we know, that is the case.
   *
   * @param storage the storage object we are trying to list
   * @return useless boolean to match the function signature
   */
  private Boolean testIam(Storage storage) {
    storage.list();
    return true;
  }

  @Override
  public StepResult doStep(FlightContext flightContext)
      throws InterruptedException, RetryException {
    // Test permissions using the user's pet SA instead of their actual account as their access
    // token may not have the cloud-platform scope required to access cloud resources.
    AuthenticatedUserRequest petSaCredentials =
        flightContext
            .getWorkingMap()
            .get(WorkspaceFlightMapKeys.PET_SA_CREDENTIALS, AuthenticatedUserRequest.class);
    String gcpProjectId = flightContext.getWorkingMap().get(GCP_PROJECT_ID, String.class);
    Storage storage = getStorage(petSaCredentials, gcpProjectId);

    var startTime = Instant.now();
    try {
      RetryUtils.getWithRetryOnException(
          () -> testIam(storage),
          RetryUtils.DEFAULT_RETRY_TOTAL_DURATION,
          RetryUtils.DEFAULT_RETRY_SLEEP_DURATION,
          0.5, /* increase wait by half again as much */
          RetryUtils.DEFAULT_RETRY_SLEEP_DURATION_MAX,
          null /* all exceptions are retried */);
    } catch (Exception e) {
      logWaitDuration(startTime);
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_FATAL, e);
    }
    logWaitDuration(startTime);
    return StepResult.getStepResultSuccess();
  }

  private void logWaitDuration(Instant startTime) {
    logger.info(
        "#=#=#=# GCP cloud context wait time in seconds: {} #=#=#=#",
        Duration.between(startTime, Instant.now()).toSeconds());
  }

  /** This is a read-only step, so nothing to undo */
  @Override
  public StepResult undoStep(FlightContext flightContext) throws InterruptedException {
    return StepResult.getStepResultSuccess();
  }
}
