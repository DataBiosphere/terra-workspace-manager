package bio.terra.workspace.service.resource.controlled.flight.clone.bucket;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import com.google.api.services.storagetransfer.v1.Storagetransfer;

/**
 * Delete a Storage Transfer Service job, which has already had an operation run to completion or
 * failure.
 *
 * <p>Preconditions: Cloning instructions are COPY_RESOURCE. Transfer Service Job exists in the
 * control plane project.
 *
 * <p>Post conditions; STS Job no longer exists.
 */
public class DeleteStorageTransferServiceJobStep implements Step {
  private final Storagetransfer storagetransfer;

  public DeleteStorageTransferServiceJobStep(Storagetransfer storagetransfer) {
    this.storagetransfer = storagetransfer;
  }

  @Override
  public StepResult doStep(FlightContext flightContext)
      throws InterruptedException, RetryException {
    FlightMap workingMap = flightContext.getWorkingMap();
    String transferJobName =
        workingMap.get(ControlledResourceKeys.STORAGE_TRANSFER_JOB_NAME, String.class);
    String controlPlaneProjectId =
        workingMap.get(ControlledResourceKeys.CONTROL_PLANE_PROJECT_ID, String.class);
    return StorageTransferServiceUtils.deleteTransferJobStepImpl(flightContext.getFlightId(), transferJobName, controlPlaneProjectId, storagetransfer);
  }

  // Nothing to undo
  @Override
  public StepResult undoStep(FlightContext flightContext) throws InterruptedException {
    return StepResult.getStepResultSuccess();
  }
}
