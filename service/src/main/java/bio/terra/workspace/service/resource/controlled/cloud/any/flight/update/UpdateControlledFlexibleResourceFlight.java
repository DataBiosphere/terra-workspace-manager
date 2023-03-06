package bio.terra.workspace.service.resource.controlled.cloud.any.flight.update;

import bio.terra.stairway.Flight;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.RetryRule;
import bio.terra.workspace.common.utils.FlightBeanBag;
import bio.terra.workspace.common.utils.RetryRules;
import bio.terra.workspace.service.resource.controlled.cloud.any.flexibleresource.ControlledFlexibleResource;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ResourceKeys;
import com.google.common.base.Preconditions;

public class UpdateControlledFlexibleResourceFlight extends Flight {

  public UpdateControlledFlexibleResourceFlight(FlightMap inputParameters, Object beanBag) {
    super(inputParameters, beanBag);

    final FlightBeanBag flightBeanBag = FlightBeanBag.getFromObject(beanBag);
    final ControlledFlexibleResource resource =
        Preconditions.checkNotNull(
            inputParameters.get(ResourceKeys.RESOURCE, ControlledFlexibleResource.class));

    // Retrieve the metadata and attributes in case of failure.
    addStep(
        new RetrieveControlledFlexibleResourceMetadataAndAttributesStep(
            flightBeanBag.getResourceDao(), resource.getWorkspaceId(), resource.getResourceId()));

    RetryRule dbRetry = RetryRules.shortDatabase();
    // Update the metadata and attributes.
    addStep(
        new UpdateControlledFlexibleResourceMetadataAndAttributesStep(
            flightBeanBag.getResourceDao(), resource),
        dbRetry);
  }
}
