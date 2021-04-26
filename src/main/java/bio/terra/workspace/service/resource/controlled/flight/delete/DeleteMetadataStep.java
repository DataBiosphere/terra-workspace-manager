package bio.terra.workspace.service.resource.controlled.flight.delete;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.db.ResourceDao;
import java.util.UUID;

/** A step for deleting the metadata that WSM stores for a controlled resource. */
public class DeleteMetadataStep implements Step {

  private final ResourceDao resourceDao;
  private final UUID workspaceId;
  private final UUID resourceId;

  public DeleteMetadataStep(ResourceDao resourceDao, UUID workspaceId, UUID resourceId) {
    this.resourceDao = resourceDao;
    this.workspaceId = workspaceId;
    this.resourceId = resourceId;
  }

  @Override
  public StepResult doStep(FlightContext flightContext)
      throws InterruptedException, RetryException {
    // deleteResource is idempotent, and here we don't care whether the resource was actually
    // deleted or just not found.
    resourceDao.deleteResource(workspaceId, resourceId);
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) throws InterruptedException {
    return StepResult.getStepResultSuccess();
  }
}
