package bio.terra.workspace.service.resource.controlled.cloud.any.flight.update;

import static bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys.UPDATE_FLEX_DATA;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.db.DbSerDes;
import bio.terra.workspace.db.ResourceDao;
import bio.terra.workspace.service.resource.controlled.cloud.any.flexibleresource.ControlledFlexibleResource;
import bio.terra.workspace.service.resource.controlled.cloud.any.flexibleresource.FlexibleResourceAttributes;
import bio.terra.workspace.service.resource.model.CloningInstructions;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ResourceKeys;
import java.util.UUID;

/**
 * A step to retrieve the name, description, and attributes of the original flex resource.
 *
 * <p>This step is to ensure we can restore back to the original state upon failure.
 */
public class UpdateControlledFlexibleResourceMetadataAndAttributesStep implements Step {
  private final ResourceDao resourceDao;

  private final ControlledFlexibleResource resource;
  private final UUID workspaceUuid;
  private final UUID resourceId;

  public UpdateControlledFlexibleResourceMetadataAndAttributesStep(
      ResourceDao resourceDao, ControlledFlexibleResource resource) {
    this.resourceDao = resourceDao;
    this.resource = resource;
    this.workspaceUuid = resource.getWorkspaceId();
    this.resourceId = resource.getResourceId();
  }

  @Override
  public StepResult doStep(FlightContext flightContext)
      throws InterruptedException, RetryException {
    final FlightMap inputParameters = flightContext.getInputParameters();
    final String resourceName = inputParameters.get(ResourceKeys.RESOURCE_NAME, String.class);
    final String resourceDescription =
        inputParameters.get(ResourceKeys.RESOURCE_DESCRIPTION, String.class);

    final String updateData =
        flightContext.getInputParameters().get(UPDATE_FLEX_DATA, String.class);
    String newAttributes =
        DbSerDes.toJson(
            new FlexibleResourceAttributes(
                resource.getTypeNamespace(), resource.getType(), updateData));

    boolean updated =
        resourceDao.updateResource(
            workspaceUuid,
            resourceId,
            resourceName,
            resourceDescription,
            newAttributes,
            resource.getCloningInstructions());
    if (!updated) {
      throw new RetryException("Failed to updated resource");
    }
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) throws InterruptedException {
    final FlightMap workingMap = flightContext.getWorkingMap();
    final String previousName = workingMap.get(ResourceKeys.PREVIOUS_RESOURCE_NAME, String.class);
    final String previousDescription =
        workingMap.get(ResourceKeys.PREVIOUS_RESOURCE_DESCRIPTION, String.class);
    final String previousAttributes =
        workingMap.get(ResourceKeys.PREVIOUS_ATTRIBUTES, String.class);
    final var previousCloningInstructions =
        workingMap.get(ResourceKeys.PREVIOUS_CLONING_INSTRUCTIONS, CloningInstructions.class);
    resourceDao.updateResource(
        workspaceUuid,
        resourceId,
        previousName,
        previousDescription,
        previousAttributes,
        previousCloningInstructions);
    return StepResult.getStepResultSuccess();
  }
}
