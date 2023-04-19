package bio.terra.workspace.service.resource.controlled.flight.clone.bucket;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;

/**
 * Remove roles from the source and destination buckets that are no longer necessary after the
 * transfer has completed. This will prevent transfer jobs from being re-run in the Console, but
 * that's not really a supported use case at the moment.
 *
 * <p>Preconditions: Cloning instructions are COPY_RESOURCE. Source and destination buckets have IAM
 * roles for the control plane project SA.
 *
 * <p>Post conditions: All added IAM roles for this flight are removed from the buckets.
 */
public class RemoveBucketRolesStep implements Step {

  private final BucketCloneRolesService bucketCloneRolesService;

  public RemoveBucketRolesStep(BucketCloneRolesService bucketCloneRolesService) {
    this.bucketCloneRolesService = bucketCloneRolesService;
  }

  @Override
  public StepResult doStep(FlightContext flightContext)
      throws InterruptedException, RetryException {
    FlightMap workingMap = flightContext.getWorkingMap();
    bucketCloneRolesService.removeAllAddedBucketRoles(workingMap);
    return StepResult.getStepResultSuccess();
  }

  // Nothing to undo. If we fail to remove them, we could retry this step, but re-adding the roles
  // would not serve any purpose, as this is the last step and the desired state is for the roles to
  // be gone.
  @Override
  public StepResult undoStep(FlightContext flightContext) throws InterruptedException {
    return StepResult.getStepResultSuccess();
  }
}
