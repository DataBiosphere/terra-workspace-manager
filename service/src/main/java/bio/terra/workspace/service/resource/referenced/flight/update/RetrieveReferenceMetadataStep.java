package bio.terra.workspace.service.resource.referenced.flight.update;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.db.ResourceDao;
import bio.terra.workspace.service.resource.WsmResource;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ResourceKeys;
import java.util.UUID;

public class RetrieveReferenceMetadataStep implements Step {
  private final ResourceDao resourceDao;
  private final UUID workspaceId;
  private final UUID resourceId;

  public RetrieveReferenceMetadataStep(ResourceDao resourceDao, UUID workspaceId, UUID resourceId) {
    this.resourceDao = resourceDao;
    this.workspaceId = workspaceId;
    this.resourceId = resourceId;
  }

  @Override
  public StepResult doStep(FlightContext context) throws InterruptedException, RetryException {
    WsmResource resource = resourceDao.getResource(workspaceId, resourceId);
    context.getWorkingMap().put(ResourceKeys.PREVIOUS_ATTRIBUTES, resource.attributesToJson());
    context.getWorkingMap().put(ResourceKeys.PREVIOUS_RESOURCE_NAME, resource.getName());
    context
        .getWorkingMap()
        .put(ResourceKeys.PREVIOUS_RESOURCE_DESCRIPTION, resource.getDescription());
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    return StepResult.getStepResultSuccess();
  }
}
