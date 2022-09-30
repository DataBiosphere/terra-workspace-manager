package bio.terra.workspace.service.folder.flights;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.db.ResourceDao;
import bio.terra.workspace.service.resource.model.WsmResource;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ReferencedResourceKeys;
import com.fasterxml.jackson.core.type.TypeReference;
import java.util.List;
import java.util.UUID;

/** Delete referenced resource. */
public class DeleteReferencedResourcesStep implements Step {

  private final ResourceDao resourceDao;
  private final UUID workspaceId;

  public DeleteReferencedResourcesStep(ResourceDao resourceDao, UUID workspaceId) {
    this.resourceDao = resourceDao;
    this.workspaceId = workspaceId;
  }

  @Override
  public StepResult doStep(FlightContext context) throws InterruptedException, RetryException {
    List<WsmResource> referencedResources =
        context
            .getInputParameters()
            .get(ReferencedResourceKeys.REFERENCED_RESOURCES_TO_DELETE, new TypeReference<>() {});
    for (var resource : referencedResources) {
      resourceDao.deleteResource(workspaceId, resource.getResourceId());
    }
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    // Nothing to undo, propagate the flight failure.
    return context.getResult();
  }
}
