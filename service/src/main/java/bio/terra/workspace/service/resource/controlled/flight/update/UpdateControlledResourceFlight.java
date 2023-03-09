package bio.terra.workspace.service.resource.controlled.flight.update;

import bio.terra.stairway.Flight;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.RetryRule;
import bio.terra.stairway.Step;
import bio.terra.workspace.common.utils.FlightBeanBag;
import bio.terra.workspace.service.resource.controlled.model.ControlledResource;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import com.google.common.base.Preconditions;

/**
 * Flight for updating a controlled resource. Some steps are resource-type-agnostic, and others
 * depend on the resource type. The latter must be passed in via the input parameters map with keys
 */
public class UpdateControlledResourceFlight extends Flight {

  // addStep is protected in Flight, so make an override that is public
  @Override
  public void addStep(Step step, RetryRule retryRule) {
    super.addStep(step, retryRule);
  }

  /**
   * @param inputParameters FlightMap of the inputs for the flight
   * @param beanBag Anonymous context meaningful to the application using Stairway
   */
  public UpdateControlledResourceFlight(FlightMap inputParameters, Object beanBag) {
    super(inputParameters, beanBag);

    final FlightBeanBag flightBeanBag = FlightBeanBag.getFromObject(beanBag);
    final ControlledResource resource =
        Preconditions.checkNotNull(
            inputParameters.get(
                WorkspaceFlightMapKeys.ResourceKeys.RESOURCE, ControlledResource.class));

    // Get a copy of the existing metadata.
    addStep(
        new RetrieveControlledResourceMetadataStep(
            flightBeanBag.getResourceDao(), resource.getWorkspaceId(), resource.getResourceId()));

    // Update the metadata (name & description of the resource).
    addStep(
        new UpdateControlledResourceMetadataStep(
            flightBeanBag.getControlledResourceMetadataManager(),
            flightBeanBag.getResourceDao(),
            resource));

    resource.addUpdateSteps(this, flightBeanBag);
  }
}
