package bio.terra.workspace.service.resource.referenced.flight.update;

import bio.terra.stairway.Flight;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.RetryRule;
import bio.terra.workspace.common.utils.FlightBeanBag;
import bio.terra.workspace.common.utils.RetryRules;
import bio.terra.workspace.service.resource.referenced.model.ReferencedResource;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ResourceKeys;

/** A flight to update the reference resource's name, description, and/or attributes. */
public class UpdateReferenceResourceFlight extends Flight {

  /**
   * Flight to update a reference resource target.
   *
   * @param inputParameters FlightMap of the inputs for the flight
   * @param beanBag Anonymous context meaningful to the application using Stairway
   */
  public UpdateReferenceResourceFlight(FlightMap inputParameters, Object beanBag) {
    super(inputParameters, beanBag);

    FlightBeanBag appContext = FlightBeanBag.getFromObject(beanBag);
    final ReferencedResource resource =
        inputParameters.get(ResourceKeys.RESOURCE, ReferencedResource.class);

    RetryRule shortDatabaseRetryRule = RetryRules.shortDatabase();
    addStep(
        new RetrieveReferenceMetadataStep(
            appContext.getResourceDao(), resource.getWorkspaceId(), resource.getResourceId()),
        shortDatabaseRetryRule);

    addStep(
        new UpdateReferenceMetadataStep(appContext.getResourceDao(), resource),
        shortDatabaseRetryRule);
  }
}
