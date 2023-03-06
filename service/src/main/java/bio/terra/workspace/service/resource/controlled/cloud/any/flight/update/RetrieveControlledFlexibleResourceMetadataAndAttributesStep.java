package bio.terra.workspace.service.resource.controlled.cloud.any.flight.update;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.db.ResourceDao;
import bio.terra.workspace.service.resource.model.WsmResource;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ResourceKeys;
import java.util.UUID;

/**
 * A step to retrieve the name, description, and attributes of the original flex resource.
 *
 * <p>This step is to ensure we can restore back to the original state upon failure.
 */
public class RetrieveControlledFlexibleResourceMetadataAndAttributesStep implements Step {
  private final ResourceDao resourceDao;
  private final UUID workspaceUuid;
  private final UUID resourceId;

  public RetrieveControlledFlexibleResourceMetadataAndAttributesStep(
      ResourceDao resourceDao, UUID workspaceUuid, UUID resourceId) {
    this.resourceDao = resourceDao;
    this.workspaceUuid = workspaceUuid;
    this.resourceId = resourceId;
  }

  @Override
  public StepResult doStep(FlightContext context) throws InterruptedException, RetryException {
    WsmResource resource = resourceDao.getResource(workspaceUuid, resourceId);
    context.getWorkingMap().put(ResourceKeys.PREVIOUS_ATTRIBUTES, resource.attributesToJson());
    context.getWorkingMap().put(ResourceKeys.PREVIOUS_RESOURCE_NAME, resource.getName());
    context
        .getWorkingMap()
        .put(ResourceKeys.PREVIOUS_RESOURCE_DESCRIPTION, resource.getDescription());
    context
        .getWorkingMap()
        .put(ResourceKeys.PREVIOUS_CLONING_INSTRUCTIONS, resource.getCloningInstructions());

    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    return StepResult.getStepResultSuccess();
  }
}
