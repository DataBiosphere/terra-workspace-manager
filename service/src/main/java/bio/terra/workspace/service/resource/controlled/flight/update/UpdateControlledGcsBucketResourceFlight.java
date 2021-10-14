package bio.terra.workspace.service.resource.controlled.flight.update;

import bio.terra.stairway.Flight;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.RetryRule;
import bio.terra.workspace.common.utils.FlightBeanBag;
import bio.terra.workspace.common.utils.RetryRules;
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
    RetryRule dbRetry = RetryRules.immediate();
    addStep(
        new RetrieveControlledResourceMetadataStep(
            flightBeanBag.getResourceDao(), resource.getWorkspaceId(), resource.getResourceId()),
        dbRetry);

    // update the metadata (name & description of resource)
    addStep(
        new UpdateControlledResourceMetadataStep(
            flightBeanBag.getControlledResourceMetadataManager(),
            flightBeanBag.getResourceDao(),
            resource.getWorkspaceId(),
            resource.getResourceId()),
        dbRetry);

    // retrieve existing attributes in case of undo later
    RetryRule gcpRetry = RetryRules.cloud();
    addStep(
        new RetrieveGcsBucketCloudAttributesStep(
            resource.castToGcsBucketResource(),
            flightBeanBag.getCrlService(),
            flightBeanBag.getGcpCloudContextService(),
            RetrievalMode.UPDATE_PARAMETERS),
        gcpRetry);

    // Update the bucket's cloud attributes
    addStep(
        new UpdateGcsBucketStep(
            resource.castToGcsBucketResource(),
            flightBeanBag.getCrlService(),
            flightBeanBag.getGcpCloudContextService()),
        gcpRetry);
  }
}
