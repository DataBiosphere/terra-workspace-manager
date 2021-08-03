package bio.terra.workspace.service.resource.controlled.flight.update;

import bio.terra.stairway.Flight;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.RetryRule;
import bio.terra.workspace.common.utils.FlightBeanBag;
import bio.terra.workspace.common.utils.RetryRules;
import bio.terra.workspace.service.job.JobMapKeys;
import bio.terra.workspace.service.resource.controlled.ControlledResource;

public class UpdateControlledBigQueryDatasetResourceFlight extends Flight {

  private final RetryRule gcpRetryRule = RetryRules.cloud();

  public UpdateControlledBigQueryDatasetResourceFlight(FlightMap inputParameters, Object beanBag) {
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
        new RetrieveBigQueryDatasetCloudAttributesStep(
            resource.castToBigQueryDatasetResource(),
            flightBeanBag.getCrlService(),
            flightBeanBag.getWorkspaceService()),
        gcpRetryRule);

    // Update the dataset's cloud attributes
    addStep(
        new UpdateBigQueryDatasetStep(
            resource.castToBigQueryDatasetResource(),
            flightBeanBag.getCrlService(),
            flightBeanBag.getWorkspaceService()),
        gcpRetryRule);
  }
}
