package bio.terra.workspace.service.resource.controlled.cloud.azure.flight;

import bio.terra.stairway.Flight;
import bio.terra.stairway.FlightMap;
import bio.terra.workspace.common.utils.FlightBeanBag;
import bio.terra.workspace.common.utils.RetryRules;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.job.JobMapKeys;
import bio.terra.workspace.service.resource.controlled.flight.update.RetrieveControlledResourceWithoutRegionStep;
import bio.terra.workspace.service.resource.controlled.flight.update.UpdateControlledResourcesRegionStep;
import bio.terra.workspace.service.workspace.model.CloudPlatform;

/** A flight to back-fill azure controlled resources that are missing the region fields. */
public class UpdateAzureControlledResourceRegionFlight extends Flight {

  /**
   * All subclasses must provide a constructor with this signature.
   *
   * @param inputParameters FlightMap of the inputs for the flight
   * @param beanBag Anonymous context meaningful to the application using Stairway
   */
  public UpdateAzureControlledResourceRegionFlight(FlightMap inputParameters, Object beanBag) {
    super(inputParameters, beanBag);
    FlightBeanBag flightBeanBag = FlightBeanBag.getFromObject(beanBag);
    AuthenticatedUserRequest userRequest =
        inputParameters.get(JobMapKeys.AUTH_USER_INFO.getKeyName(), AuthenticatedUserRequest.class);
    addStep(
        new RetrieveControlledResourceWithoutRegionStep(
            CloudPlatform.AZURE, flightBeanBag.getResourceDao()),
        RetryRules.shortDatabase());
    addStep(
        new RetrieveAzureCloudContexts(flightBeanBag.getAzureCloudContextService()),
        RetryRules.shortDatabase());
    addStep(
        new RetrieveAzureResourcesRegionStep(
            flightBeanBag.getAzureConfig(),
            flightBeanBag.getCrlService(),
            flightBeanBag.getResourceDao(),
            flightBeanBag.getLandingZoneApiDispatch(),
            userRequest),
        RetryRules.shortExponential());
    addStep(new UpdateControlledResourcesRegionStep(flightBeanBag.getResourceDao()));
  }
}
