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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;

/** Do not complete the cloud context creation until the project permissions have propagated. */
public class WaitForProjectPermissionsStep implements Step {
  private final Logger logger = LoggerFactory.getLogger(WaitForProjectPermissionsStep.class);
  private final AuthenticatedUserRequest userRequest;

  public WaitForProjectPermissionsStep(AuthenticatedUserRequest userRequest) {
    this.userRequest = userRequest;
  }

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
    String gcpProjectId = flightContext.getWorkingMap().get(GCP_PROJECT_ID, String.class);
    Storage storage = getStorage(userRequest, gcpProjectId);

    try {
      RetryUtils.getWithRetryOnException(() -> testIam(storage),
        Duration.ofMinutes(20), /* total duration */
        RetryUtils.DEFAULT_SLEEP_DURATION,
        null /* all exceptions are retried */);
    } catch (Exception e) {
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_FATAL, e);
    }
    return StepResult.getStepResultSuccess();
  }

  /** This is a read-only step, so nothing to undo */
  @Override
  public StepResult undoStep(FlightContext flightContext) throws InterruptedException {
    return StepResult.getStepResultSuccess();
  }
}
