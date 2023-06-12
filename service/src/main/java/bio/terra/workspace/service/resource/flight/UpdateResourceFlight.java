package bio.terra.workspace.service.resource.flight;

import bio.terra.stairway.Flight;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.RetryRule;
import bio.terra.stairway.Step;
import bio.terra.workspace.common.utils.FlightBeanBag;
import bio.terra.workspace.common.utils.FlightUtils;
import bio.terra.workspace.common.utils.RetryRules;
import bio.terra.workspace.service.resource.model.CommonUpdateParameters;
import bio.terra.workspace.service.resource.model.WsmResource;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;

/**
 * Common flight for updating a resource. Some steps are resource-type-agnostic, and others depend
 * on the resource type. The latter must be passed in via the input parameters map with keys
 */
public class UpdateResourceFlight extends Flight {

  // addStep is protected in Flight, so make an override that is public
  @Override
  public void addStep(Step step, RetryRule retryRule) {
    super.addStep(step, retryRule);
  }

  @Override
  public void addStep(Step step) {
    super.addStep(step);
  }

  /**
   * @param inputParameters FlightMap of the inputs for the flight
   * @param beanBag Anonymous context meaningful to the application using Stairway
   */
  public UpdateResourceFlight(FlightMap inputParameters, Object beanBag) {
    super(inputParameters, beanBag);

    FlightBeanBag flightBeanBag = FlightBeanBag.getFromObject(beanBag);
    WsmResource resource =
        FlightUtils.getRequired(
            inputParameters, WorkspaceFlightMapKeys.ResourceKeys.RESOURCE, WsmResource.class);
    CommonUpdateParameters commonUpdateParameters =
        FlightUtils.getRequired(
            inputParameters,
            WorkspaceFlightMapKeys.ResourceKeys.COMMON_UPDATE_PARAMETERS,
            CommonUpdateParameters.class);
    RetryRule dbRetry = RetryRules.shortDatabase();

    // Get database row; mark as updating; build the DbUpdater object, setting
    // the common update parameters and save in working map
    addStep(
        new UpdateStartStep(
            flightBeanBag.getResourceDao(),
            resource.getWorkspaceId(),
            resource.getResourceId(),
            commonUpdateParameters),
        dbRetry);

    resource.addUpdateSteps(this, flightBeanBag);

    // Apply updates and reset state
    addStep(
        new UpdateFinishStep(
            flightBeanBag.getResourceDao(), resource.getWorkspaceId(), resource.getResourceId()),
        dbRetry);
  }
}
