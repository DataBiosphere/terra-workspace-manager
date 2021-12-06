package bio.terra.workspace.service.resource.controlled.flight.create;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.db.ResourceDao;
import bio.terra.workspace.service.resource.controlled.AccessScopeType;
import bio.terra.workspace.service.resource.controlled.ControlledResource;
import bio.terra.workspace.service.resource.controlled.PrivateResourceState;

public class MarkPrivateResourceReadyStep implements Step {

  private final ControlledResource resource;
  private final ResourceDao resourceDao;

  public MarkPrivateResourceReadyStep(ControlledResource resource, ResourceDao resourceDao) {
    this.resource = resource;
    this.resourceDao = resourceDao;
  }

  @Override
  public StepResult doStep(FlightContext context) throws InterruptedException, RetryException {
    if (resource.getAccessScope() == AccessScopeType.ACCESS_SCOPE_PRIVATE) {
      resourceDao.setPrivateResourceState(resource, PrivateResourceState.ACTIVE);
    }
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    // Set state back to INITIALIZING in case an earlier step fails to undo.
    // The first step of this flight enforces resource uniqueness, so we can be sure there are no
    // other flights this could be clobbering.
    if (resource.getAccessScope() == AccessScopeType.ACCESS_SCOPE_PRIVATE) {
      resourceDao.setPrivateResourceState(resource, PrivateResourceState.INITIALIZING);
    }
    return StepResult.getStepResultSuccess();
  }
}
