package bio.terra.workspace.service.resource.controlled.cloud.gcp.flight;

import bio.terra.stairway.Flight;
import bio.terra.stairway.FlightMap;
import bio.terra.workspace.common.utils.FlightBeanBag;
import bio.terra.workspace.common.utils.RetryRules;
import bio.terra.workspace.service.resource.controlled.flight.update.RetrieveControlledResourceWithoutRegionStep;
import bio.terra.workspace.service.resource.controlled.flight.update.UpdateResourcesRegionStep;
import bio.terra.workspace.service.workspace.model.CloudPlatform;

// TODO (PF-2368): clean this up once back-fill is done in all Terra environment.
public class UpdateGcpControlledResourceRegionFlight extends Flight {

  /**
   * All subclasses must provide a constructor with this signature.
   *
   * @param inputParameters FlightMap of the inputs for the flight
   * @param beanBag Anonymous context meaningful to the application using Stairway
   */
  public UpdateGcpControlledResourceRegionFlight(FlightMap inputParameters, Object beanBag) {
    super(inputParameters, beanBag);
    FlightBeanBag flightBeanBag = FlightBeanBag.getFromObject(beanBag);
    addStep(
        new RetrieveControlledResourceWithoutRegionStep(
            CloudPlatform.GCP, flightBeanBag.getResourceDao()),
        RetryRules.shortDatabase());
    addStep(
        new RetrieveGcpResourcesRegionStep(
            flightBeanBag.getCrlService(), flightBeanBag.getGcpCloudContextService()),
        RetryRules.shortExponential());
    addStep(new UpdateResourcesRegionStep(flightBeanBag.getResourceDao()));
  }
}
