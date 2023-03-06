package bio.terra.workspace.service.resource.controlled.cloud.any.flight.update;

import static bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys.UPDATE_FLEX_DATA;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.db.DbSerDes;
import bio.terra.workspace.db.ResourceDao;
import bio.terra.workspace.service.resource.controlled.cloud.any.flexibleresource.ControlledFlexibleResource;
import bio.terra.workspace.service.resource.controlled.cloud.any.flexibleresource.FlexibleResourceAttributes;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;

public class UpdateControlledFlexibleResourceAttributesStep implements Step {
  private final ResourceDao resourceDao;
  private final ControlledFlexibleResource flexResource;

  public UpdateControlledFlexibleResourceAttributesStep(
      ResourceDao resourceDao, ControlledFlexibleResource flexResource) {
    this.resourceDao = resourceDao;
    this.flexResource = flexResource;
  }

  @Override
  public StepResult doStep(FlightContext context) throws InterruptedException, RetryException {
    String previousAttributes = flexResource.attributesToJson();
    context
        .getWorkingMap()
        .put(WorkspaceFlightMapKeys.ResourceKeys.PREVIOUS_ATTRIBUTES, previousAttributes);

    final String updateData = context.getInputParameters().get(UPDATE_FLEX_DATA, String.class);

    if (updateData == null) {
      return StepResult.getStepResultSuccess();
    }

    String newAttributes =
        DbSerDes.toJson(
            new FlexibleResourceAttributes(
                flexResource.getTypeNamespace(), flexResource.getType(), updateData));
    boolean updated =
        resourceDao.updateResource(
            flexResource.getWorkspaceId(),
            flexResource.getResourceId(),
            null,
            null,
            newAttributes,
            null);

    if (!updated) {
      throw new RetryException("Failed to update flex resource with new data.");
    }
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) throws InterruptedException {
    String previousAttributes =
        flightContext
            .getWorkingMap()
            .get(WorkspaceFlightMapKeys.ResourceKeys.PREVIOUS_ATTRIBUTES, String.class);
    resourceDao.updateResource(
        flexResource.getWorkspaceId(),
        flexResource.getResourceId(),
        null,
        null,
        previousAttributes,
        null);
    return StepResult.getStepResultSuccess();
  }
}
