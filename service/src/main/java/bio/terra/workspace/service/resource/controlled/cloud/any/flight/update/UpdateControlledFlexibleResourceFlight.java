package bio.terra.workspace.service.resource.controlled.cloud.any.flight.update;

import bio.terra.stairway.Flight;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.RetryRule;
import bio.terra.workspace.common.utils.FlightBeanBag;
import bio.terra.workspace.common.utils.RetryRules;
import bio.terra.workspace.service.resource.controlled.flight.update.RetrieveControlledResourceMetadataStep;
import bio.terra.workspace.service.resource.controlled.flight.update.UpdateControlledResourceMetadataStep;
import bio.terra.workspace.service.resource.controlled.model.ControlledResource;
import bio.terra.workspace.service.resource.model.WsmResourceType;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ResourceKeys;
import com.google.common.base.Preconditions;

// TODO (PF-2553): Refactor flight into generic update controlled resource flight.
// This flight is purposefully verbose to match future refactoring.

public class UpdateControlledFlexibleResourceFlight extends Flight {

  public UpdateControlledFlexibleResourceFlight(FlightMap inputParameters, Object beanBag) {
    super(inputParameters, beanBag);

    final FlightBeanBag flightBeanBag = FlightBeanBag.getFromObject(beanBag);
    final ControlledResource resource =
        Preconditions.checkNotNull(
            inputParameters.get(ResourceKeys.RESOURCE, ControlledResource.class));

    // TODO (PF-2553): Refactor these two steps into the generic update controlled resource flight.
    // get copy of existing metadata
    addStep(
        new RetrieveControlledResourceMetadataStep(
            flightBeanBag.getResourceDao(), resource.getWorkspaceId(), resource.getResourceId()));

    // update the metadata (name & description of resource)
    addStep(
        new UpdateControlledResourceMetadataStep(
            flightBeanBag.getControlledResourceMetadataManager(),
            flightBeanBag.getResourceDao(),
            resource));

    RetryRule dbRetry = RetryRules.shortDatabase();
    // Update the flex resource's  attributes
    addStep(
        new UpdateControlledFlexibleResourceAttributesStep(
            flightBeanBag.getResourceDao(),
            resource.castByEnum(WsmResourceType.CONTROLLED_FLEXIBLE_RESOURCE)),
        dbRetry);
  }
}
