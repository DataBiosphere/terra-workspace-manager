package bio.terra.workspace.service.resource.referenced.flight.create;

import bio.terra.stairway.Flight;
import bio.terra.stairway.FlightMap;
import bio.terra.workspace.common.utils.FlightBeanBag;
import bio.terra.workspace.common.utils.FlightUtils;
import bio.terra.workspace.common.utils.RetryRules;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ResourceKeys;

public class CreateReferenceResourceFlight extends Flight {

  public CreateReferenceResourceFlight(FlightMap inputParameters, Object beanBag) {
    super(inputParameters, beanBag);

    FlightUtils.validateRequiredEntries(
        inputParameters, ResourceKeys.RESOURCE, ResourceKeys.RESOURCE_TYPE);

    FlightBeanBag appContext = FlightBeanBag.getFromObject(beanBag);

    // Store the reference metadata
    addStep(
        new CreateReferenceMetadataStep(appContext.getResourceDao()), RetryRules.shortDatabase());
  }
}
