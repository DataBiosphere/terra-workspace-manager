package bio.terra.workspace.service.workspace.flight;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.resource.controlled.ControlledResource;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import java.util.List;

/**
 * A step which deletes the Sam data of the controlled resources provided by {@link
 * ListControlledResourceIdsStep} Multiple deletions are run in parallel as resources are
 * independent.
 */
public class DeleteControlledSamResourcesStep implements Step {

  private final SamService samService;
  private final AuthenticatedUserRequest userRequest;

  public DeleteControlledSamResourcesStep(
      SamService samService, AuthenticatedUserRequest userRequest) {
    this.samService = samService;
    this.userRequest = userRequest;
  }

  @Override
  public StepResult doStep(FlightContext flightContext)
      throws InterruptedException, RetryException {
    FlightMap workingMap = flightContext.getWorkingMap();

    @SuppressWarnings("unchecked")
    List<ControlledResource> controlledResourceList =
        workingMap.get(ControlledResourceKeys.CONTROLLED_RESOURCE_LIST, List.class);

    try {
      // Deleting each resource takes a call to Sam. As there may be many many resources and they
      // are all independent, running these calls in parallel can save time.
      controlledResourceList.parallelStream()
          .forEach(
              controlledResource -> {
                try {
                  samService.deleteControlledResource(controlledResource, userRequest);
                } catch (InterruptedException e) {
                  // This function must handle the checked InterruptedException, even though the
                  // Step as a whole is fine surfacing it. Here, we wrap the InterruptedException in
                  // an unchecked exception, smuggle it out, then catch and throw it later.
                  throw new UncheckedInterruptedException(e);
                }
              });
    } catch (UncheckedInterruptedException e) {
      throw e.getException();
    }
    return StepResult.getStepResultSuccess();
  }

  /**
   * An unchecked exception wrapping a checked InterruptedException. This is a workaround for the
   * stream above which cannot handle checked exceptions, even though that's really what we want.
   */
  private static class UncheckedInterruptedException extends RuntimeException {
    private final InterruptedException interruptedException;

    public UncheckedInterruptedException(InterruptedException interruptedException) {
      this.interruptedException = interruptedException;
    }

    public InterruptedException getException() {
      return interruptedException;
    }
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) throws InterruptedException {
    // Resource deletion can't be undone, so this just surfaces the error from the DO direction
    // instead.
    return flightContext.getResult();
  }
}
