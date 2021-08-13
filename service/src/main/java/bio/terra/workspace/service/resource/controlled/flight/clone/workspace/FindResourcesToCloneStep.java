package bio.terra.workspace.service.resource.controlled.flight.clone.workspace;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.db.ResourceDao;
import bio.terra.workspace.service.resource.WsmResource;
import bio.terra.workspace.service.resource.WsmResourceType;
import bio.terra.workspace.service.resource.model.StewardshipType;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class FindResourcesToCloneStep implements Step {

  private final ResourceDao resourceDao;

  public FindResourcesToCloneStep(ResourceDao resourceDao) {
    this.resourceDao = resourceDao;
  }

  @Override
  public StepResult doStep(FlightContext context) throws InterruptedException, RetryException {
    final var sourceWorkspaceId =
        context.getInputParameters().get(ControlledResourceKeys.SOURCE_WORKSPACE_ID, UUID.class);
    int offset = 0;
    final int limit = 100;
    List<WsmResource> batch;
    final List<WsmResource> result = new ArrayList<>();
    do {
      batch = resourceDao.enumerateResources(sourceWorkspaceId, null, null, offset, limit);
      offset += limit;
      final List<WsmResource> cloneableResources =
          batch.stream().filter(this::isCloneable).collect(Collectors.toList());
      result.addAll(cloneableResources);
    } while (batch.size() == limit);
    context.getWorkingMap().put(ControlledResourceKeys.RESOURCES_TO_CLONE, result);
    return StepResult.getStepResultSuccess();
  }

  // Nothing to undo; no side effects.
  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    return StepResult.getStepResultSuccess();
  }

  private boolean isCloneable(WsmResource resource) {
    return StewardshipType.REFERENCED == resource.getStewardshipType()
        || (StewardshipType.CONTROLLED == resource.getStewardshipType()
            && (WsmResourceType.GCS_BUCKET == resource.getResourceType()
                || WsmResourceType.BIG_QUERY_DATASET == resource.getResourceType()));
  }
}
