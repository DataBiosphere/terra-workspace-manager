package bio.terra.workspace.service.resource.controlled.cloud.aws.flight;

import static bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.IS_WET_RUN;

import bio.terra.stairway.Flight;
import bio.terra.stairway.FlightMap;
import bio.terra.workspace.common.utils.FlightBeanBag;
import bio.terra.workspace.common.utils.FlightUtils;
import bio.terra.workspace.common.utils.RetryRules;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.job.JobMapKeys;
import bio.terra.workspace.service.resource.controlled.flight.update.RetrieveControlledResourceWithoutRegionStep;
import bio.terra.workspace.service.resource.controlled.flight.update.UpdateControlledResourcesRegionStep;
import bio.terra.workspace.service.workspace.model.CloudPlatform;

/** A flight to back-fill aws controlled resources that are missing the region fields. */
public class UpdateAwsControlledResourceRegionFlight extends Flight {

  /**
   * All subclasses must provide a constructor with this signature.
   *
   * @param inputParameters FlightMap of the inputs for the flight
   * @param beanBag Anonymous context meaningful to the application using Stairway
   */
  public UpdateAwsControlledResourceRegionFlight(FlightMap inputParameters, Object beanBag) {
    super(inputParameters, beanBag);
    FlightBeanBag flightBeanBag = FlightBeanBag.getFromObject(beanBag);
    AuthenticatedUserRequest userRequest =
        inputParameters.get(JobMapKeys.AUTH_USER_INFO.getKeyName(), AuthenticatedUserRequest.class);
    boolean isWetRun = FlightUtils.getRequired(inputParameters, IS_WET_RUN, Boolean.class);
    addStep(
        new RetrieveControlledResourceWithoutRegionStep(
            CloudPlatform.AWS, flightBeanBag.getResourceDao()),
        RetryRules.shortDatabase());
    addStep(
        new RetrieveAwsCloudContexts(flightBeanBag.getAwsCloudContextService()),
        RetryRules.shortDatabase());
    addStep(
        new RetrieveAwsResourcesRegionStep(flightBeanBag.getAwsConfiguration()),
        RetryRules.shortExponential());
    addStep(new UpdateControlledResourcesRegionStep(flightBeanBag.getResourceDao(), isWetRun));
  }
}
