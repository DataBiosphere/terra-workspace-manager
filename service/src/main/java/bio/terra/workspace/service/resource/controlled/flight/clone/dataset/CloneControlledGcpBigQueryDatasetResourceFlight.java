package bio.terra.workspace.service.resource.controlled.flight.clone.dataset;

import bio.terra.stairway.Flight;
import bio.terra.stairway.FlightMap;
import bio.terra.workspace.common.utils.FlightBeanBag;
import bio.terra.workspace.common.utils.RetryRules;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.job.JobMapKeys;
import bio.terra.workspace.service.resource.controlled.ControlledBigQueryDatasetResource;
import bio.terra.workspace.service.resource.controlled.ControlledResource;
import bio.terra.workspace.service.resource.controlled.flight.clone.CheckControlledResourceAuthStep;
import bio.terra.workspace.service.resource.controlled.flight.update.RetrieveControlledResourceMetadataStep;

public class CloneControlledGcpBigQueryDatasetResourceFlight extends Flight {

  public CloneControlledGcpBigQueryDatasetResourceFlight(
      FlightMap inputParameters, Object applicationContext) {
    super(inputParameters, applicationContext);
    final FlightBeanBag flightBeanBag = FlightBeanBag.getFromObject(applicationContext);
    final ControlledResource sourceResource =
        inputParameters.get(JobMapKeys.REQUEST.getKeyName(), ControlledResource.class);
    final AuthenticatedUserRequest userRequest =
        inputParameters.get(JobMapKeys.AUTH_USER_INFO.getKeyName(), AuthenticatedUserRequest.class);

    // Flight Plan
    // 1. Validate user has read access to the source object
    // 2. Gather controlled resource metadata for source object
    // 3. Gather creation parameters from existing object
    // 4. Launch sub-flight to create appropriate resource
    addStep(
        new CheckControlledResourceAuthStep(
            sourceResource, flightBeanBag.getControlledResourceMetadataManager(), userRequest),
        RetryRules.shortExponential());
    addStep(
        new RetrieveControlledResourceMetadataStep(
            flightBeanBag.getResourceDao(),
            sourceResource.getWorkspaceId(),
            sourceResource.getResourceId()));
    final ControlledBigQueryDatasetResource sourceDataset =
        sourceResource.castToBigQueryDatasetResource();

    addStep(
        new RetrieveBigQueryDatasetCloudAttributesStep(
            sourceDataset,
            flightBeanBag.getCrlService(),
            flightBeanBag.getGcpCloudContextService(),
            userRequest));

    addStep(
        new CopyBigQueryDatasetDefinitionStep(
            sourceDataset,
            flightBeanBag.getControlledResourceService(),
            userRequest,
            flightBeanBag.getGcpCloudContextService()));
    addStep(
        new CreateTableCopyJobsStep(
            flightBeanBag.getCrlService(),
            flightBeanBag.getGcpCloudContextService(),
            sourceDataset),
        RetryRules.cloud());
    addStep(
        new CompleteTableCopyJobsStep(flightBeanBag.getCrlService()),
        RetryRules.cloudLongRunning());
  }
}
