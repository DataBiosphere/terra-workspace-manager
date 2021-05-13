package bio.terra.workspace.service.resource.controlled.flight.update;

import bio.terra.stairway.Flight;
import bio.terra.stairway.FlightMap;
import bio.terra.workspace.common.utils.FlightBeanBag;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.job.JobMapKeys;
import bio.terra.workspace.service.resource.controlled.ControlledResource;

public class UpdateControlledGcsBucketResourceFlight extends Flight {

  public UpdateControlledGcsBucketResourceFlight(FlightMap inputParameters, Object beanBag) {
    super(inputParameters, beanBag);

    final FlightBeanBag flightBeanBag = FlightBeanBag.getFromObject(beanBag);
    final ControlledResource resource =
        inputParameters.get(JobMapKeys.REQUEST.getKeyName(), ControlledResource.class);
    final AuthenticatedUserRequest userRequest =
        inputParameters.get(JobMapKeys.AUTH_USER_INFO.getKeyName(), AuthenticatedUserRequest.class);

    // get copy of existing metadata
    addStep(
        new RetrieveControlledResourceMetadataStep(
            flightBeanBag.getResourceDao(), resource.getResourceId(), resource.getWorkspaceId()));

    // update the metadata (name & description of resource)
    addStep(
        new UpdateControlledResourceMetadataStep(
            flightBeanBag.getControlledResourceMetadataManager(),
            flightBeanBag.getResourceDao(),
            resource.getResourceId(),
            resource.getWorkspaceId()));

    // retrieve existing attributes in case of undo later
    addStep(
        new RetrieveGcsBucketCloudAttributesStep(
            resource.castToGcsBucketResource(),
            flightBeanBag.getCrlService(),
            flightBeanBag.getWorkspaceService()));

    // Update the bucket's cloud attributes
    addStep(
        new UpdateGcsBucketStep(
            resource.castToGcsBucketResource(),
            flightBeanBag.getCrlService(),
            flightBeanBag.getWorkspaceService()));
  }
}
