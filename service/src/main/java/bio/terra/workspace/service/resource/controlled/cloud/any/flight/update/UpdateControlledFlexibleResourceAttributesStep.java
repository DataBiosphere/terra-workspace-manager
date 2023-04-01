package bio.terra.workspace.service.resource.controlled.cloud.any.flight.update;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.common.utils.FlightUtils;
import bio.terra.workspace.db.model.DbUpdater;
import bio.terra.workspace.service.resource.controlled.cloud.any.flexibleresource.FlexibleResourceAttributes;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;

/**
 * The update parameter for flexible resources is simply the decoded string of the updated data. It
 * may be null.
 */
public class UpdateControlledFlexibleResourceAttributesStep implements Step {
  public UpdateControlledFlexibleResourceAttributesStep() {}

  @Override
  public StepResult doStep(FlightContext context) throws InterruptedException, RetryException {
    DbUpdater dbUpdater =
        FlightUtils.getRequired(
            context.getWorkingMap(),
            WorkspaceFlightMapKeys.ResourceKeys.DB_UPDATER,
            DbUpdater.class);
    String updateData =
        context
            .getInputParameters()
            .get(WorkspaceFlightMapKeys.ResourceKeys.UPDATE_PARAMETERS, String.class);
    if (updateData == null) {
      return StepResult.getStepResultSuccess();
    }

    // Compute the new attributes and save them in the DbUpdater
    FlexibleResourceAttributes originalAttributes =
        dbUpdater.getOriginalAttributes(FlexibleResourceAttributes.class);
    dbUpdater.updateAttributes(
        new FlexibleResourceAttributes(
            originalAttributes.getTypeNamespace(), originalAttributes.getType(), updateData));
    // Save the mutated updater in the map
    context.getWorkingMap().put(WorkspaceFlightMapKeys.ResourceKeys.DB_UPDATER, dbUpdater);
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) throws InterruptedException {
    // Nothing to undo - the do step mutates the DbUpdater, but does not change any
    // persistent state.
    return StepResult.getStepResultSuccess();
  }
}
