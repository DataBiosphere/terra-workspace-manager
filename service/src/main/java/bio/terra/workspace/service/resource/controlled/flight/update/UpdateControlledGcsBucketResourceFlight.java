package bio.terra.workspace.service.resource.controlled.flight.update;

import bio.terra.stairway.Flight;
import bio.terra.stairway.FlightMap;
import bio.terra.workspace.common.utils.FlightBeanBag;
import bio.terra.workspace.service.job.JobMapKeys;
import bio.terra.workspace.service.resource.controlled.ControlledResource;
import bio.terra.workspace.service.resource.controlled.flight.update.RetrieveGcsBucketCloudAttributesStep.RetrievalMode;

public class UpdateControlledGcsBucketResourceFlight extends Flight {

  public UpdateControlledGcsBucketResourceFlight(FlightMap inputParameters, Object beanBag) {
    super(inputParameters, beanBag);

    final FlightBeanBag flightBeanBag = FlightBeanBag.getFromObject(beanBag);
    final ControlledResource resource =
        inputParameters.get(JobMapKeys.REQUEST.getKeyName(), ControlledResource.class);

    // get copy of existing metadata
    addStep(
        new RetrieveControlledResourceMetadataStep(
            flightBeanBag.getResourceDao(), resource.getWorkspaceId(), resource.getResourceId()));

    // update the metadata (name & description of resource)
    addStep(
        new UpdateControlledResourceMetadataStep(
            flightBeanBag.getControlledResourceMetadataManager(),
            flightBeanBag.getResourceDao(),
            resource.getWorkspaceId(),
            resource.getResourceId()));

    // retrieve existing attributes in case of undo later
    addStep(
        new RetrieveGcsBucketCloudAttributesStep(
            resource.castToGcsBucketResource(),
            flightBeanBag.getCrlService(),
            flightBeanBag.getWorkspaceService(),
            RetrievalMode.UPDATE_PARAMETERS));

    // Update the bucket's cloud attributes
    addStep(
        new UpdateGcsBucketStep(
            resource.castToGcsBucketResource(),
            flightBeanBag.getCrlService(),
            flightBeanBag.getWorkspaceService()));
  }
}
