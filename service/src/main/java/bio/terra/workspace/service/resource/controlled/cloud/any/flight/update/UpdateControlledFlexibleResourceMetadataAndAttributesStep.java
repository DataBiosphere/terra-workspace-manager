package bio.terra.workspace.service.resource.controlled.cloud.any.flight.update;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.common.utils.FlightUtils;
import bio.terra.workspace.db.DbSerDes;
import bio.terra.workspace.db.ResourceDao;
import bio.terra.workspace.service.resource.controlled.cloud.any.flexibleresource.ControlledFlexibleResource;
import bio.terra.workspace.service.resource.controlled.cloud.any.flexibleresource.FlexibleResourceAttributes;
import bio.terra.workspace.service.resource.model.CloningInstructions;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ResourceKeys;
import org.springframework.http.HttpStatus;

import static bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys.UPDATE_FLEX_DATA;

/**
 * A step to retrieve the name, description, and attributes of the original resource reference.
 *
 * <p>This step is to make sure that if update fail, we can restore back to the original resource.
 */
public class UpdateControlledFlexibleResourceMetadataAndAttributesStep implements Step {
  private final ResourceDao resourceDao;

  private final ControlledFlexibleResource resource;

  public UpdateControlledFlexibleResourceMetadataAndAttributesStep(
      ResourceDao resourceDao, ControlledFlexibleResource resource) {
    this.resourceDao = resourceDao;
    this.resource = resource;
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
            resource.getWorkspaceId(),
            resource.getResourceId(),
            resourceName,
            resourceDescription,
            newAttributes,
            resource.getCloningInstructions());
    if (!updated) {
      throw new RetryException("Failed to updated resource");
    }
    //    FlightUtils.setResponse(flightContext, updated, HttpStatus.OK);
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
        resource.getWorkspaceId(),
        resource.getResourceId(),
        previousName,
        previousDescription,
        previousAttributes,
        previousCloningInstructions);
    return StepResult.getStepResultSuccess();
  }
}
