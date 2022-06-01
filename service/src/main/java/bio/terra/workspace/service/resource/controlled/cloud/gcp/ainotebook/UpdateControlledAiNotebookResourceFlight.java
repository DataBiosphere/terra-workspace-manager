package bio.terra.workspace.service.resource.controlled.cloud.gcp.ainotebook;

import bio.terra.stairway.Flight;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.RetryRule;
import bio.terra.workspace.common.utils.FlightBeanBag;
import bio.terra.workspace.common.utils.RetryRules;
import bio.terra.workspace.service.resource.controlled.flight.update.RetrieveControlledResourceMetadataStep;
import bio.terra.workspace.service.resource.controlled.flight.update.UpdateControlledResourceMetadataStep;
import bio.terra.workspace.service.resource.controlled.model.ControlledResource;
import bio.terra.workspace.service.resource.model.WsmResourceType;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ResourceKeys;

/** {@link Flight} to update {@link ControlledAiNotebookInstanceResource}. */
public class UpdateControlledAiNotebookResourceFlight extends Flight {

  /**
   * All subclasses must provide a constructor with this signature.
   *
   * @param inputParameters FlightMap of the inputs for the flight
   * @param beanBag Anonymous context meaningful to the application using Stairway
   */
  public UpdateControlledAiNotebookResourceFlight(FlightMap inputParameters, Object beanBag) {
    super(inputParameters, beanBag);

    final FlightBeanBag flightBeanBag = FlightBeanBag.getFromObject(beanBag);
    final ControlledResource resource =
        inputParameters.get(ResourceKeys.RESOURCE, ControlledResource.class);

    // get copy of existing metadata
    RetryRule dbRetry = RetryRules.shortDatabase();
    addStep(
        new RetrieveControlledResourceMetadataStep(
            flightBeanBag.getResourceDao(), resource.getWorkspaceId(), resource.getResourceId()),
        dbRetry);

    // update the metadata (name & description of resource)
    addStep(
        new UpdateControlledResourceMetadataStep(
            flightBeanBag.getControlledResourceMetadataManager(),
            flightBeanBag.getResourceDao(),
            resource),
        dbRetry);

    ControlledAiNotebookInstanceResource aiNotebookResource =
        resource.castByEnum(WsmResourceType.CONTROLLED_GCP_AI_NOTEBOOK_INSTANCE);
    // retrieve existing attributes in case of undo later
    RetryRule gcpRetry = RetryRules.cloud();
    addStep(
        new RetrieveAiNotebookResourceAttributesStep(
            aiNotebookResource,
            flightBeanBag.getCrlService(),
            flightBeanBag.getGcpCloudContextService()),
        gcpRetry);
    // update ai notebook
    addStep(
        new UpdateAiNotebookAttributesStep(
            aiNotebookResource,
            flightBeanBag.getCrlService(),
            flightBeanBag.getGcpCloudContextService()),
        gcpRetry);
  }
}
