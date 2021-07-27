package bio.terra.workspace.service.resource.controlled.flight.clone.bucket;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.service.resource.model.CloningInstructions;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;

/**
 * Remove roles from the source and destination buckets that are no longer necessary after the
 * transfer has completed. This will prevent transfer jobs from being re-run in the Console, but
 * that's not really a supported use case at the moment.
 */
public class RemoveBucketRolesStep implements Step {

  private final BucketCloneRolesComponent bucketCloneRolesService;

  public RemoveBucketRolesStep(BucketCloneRolesComponent bucketCloneRolesService) {
    this.bucketCloneRolesService = bucketCloneRolesService;
  }

  @Override
  public StepResult doStep(FlightContext flightContext)
      throws InterruptedException, RetryException {
    final FlightMap workingMap = flightContext.getWorkingMap();
    // The remove call is harmless if the roles were never added, so we don't
    // technically need to check the cloning instructions except for performance.
    final CloningInstructions effectiveCloningInstructions =
        workingMap.get(ControlledResourceKeys.CLONING_INSTRUCTIONS, CloningInstructions.class);
    // This step is only run for full resource clones
    if (CloningInstructions.COPY_RESOURCE != effectiveCloningInstructions) {
      return StepResult.getStepResultSuccess();
    }

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
