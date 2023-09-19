package bio.terra.workspace.service.workspace.flight.cloud.gcp;

import bio.terra.stairway.Flight;
import bio.terra.stairway.FlightMap;
import bio.terra.workspace.common.utils.FlightBeanBag;
import bio.terra.workspace.common.utils.FlightUtils;
import bio.terra.workspace.service.resource.controlled.model.ControlledResource;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;

/** Flight to remove native access to private resources. Runs the steps returned by ControlledResource.getRemoveNativeAccessSteps. A check should be run ahead of time to ensure getRemoveNativeAccessSteps does not return an empty list to avoid empty flights. */
public class RemoveNativeAccessToPrivateResourcesFlight extends Flight {

  public RemoveNativeAccessToPrivateResourcesFlight(FlightMap inputParameters, Object beanBag) {
    super(inputParameters, beanBag);
    FlightBeanBag flightBeanBag = FlightBeanBag.getFromObject(beanBag);

    var resource =
        FlightUtils.getRequired(
            inputParameters, ControlledResourceKeys.RESOURCE, ControlledResource.class);
    resource
        .getRemoveNativeAccessSteps(flightBeanBag)
        .forEach(p -> addStep(p.step(), p.retryRule()));
  }
}
