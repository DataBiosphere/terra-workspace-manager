package bio.terra.workspace.service.resource.controlled.flight.clone.dataset;

import bio.terra.stairway.Flight;
import bio.terra.stairway.FlightMap;
import bio.terra.workspace.common.utils.FlightBeanBag;
import bio.terra.workspace.common.utils.FlightUtils;
import bio.terra.workspace.common.utils.RetryRules;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.job.JobMapKeys;
import bio.terra.workspace.service.policy.flight.MergePolicyAttributesDryRunStep;
import bio.terra.workspace.service.policy.flight.MergePolicyAttributesStep;
import bio.terra.workspace.service.policy.flight.ValidateWorkspaceAgainstPolicyStep;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.bqdataset.ControlledBigQueryDatasetResource;
import bio.terra.workspace.service.resource.controlled.flight.clone.CheckControlledResourceAuthStep;
import bio.terra.workspace.service.resource.controlled.flight.update.RetrieveControlledResourceMetadataStep;
import bio.terra.workspace.service.resource.controlled.model.ControlledResource;
import bio.terra.workspace.service.resource.model.CloningInstructions;
import bio.terra.workspace.service.resource.model.WsmResourceType;
import bio.terra.workspace.service.resource.referenced.flight.create.CreateReferenceMetadataStep;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ResourceKeys;
import java.util.Optional;
import java.util.UUID;

public class CloneControlledGcpBigQueryDatasetResourceFlight extends Flight {

  public CloneControlledGcpBigQueryDatasetResourceFlight(
      FlightMap inputParameters, Object applicationContext) {
    super(inputParameters, applicationContext);

    FlightUtils.validateRequiredEntries(
        inputParameters,
        ResourceKeys.RESOURCE,
        JobMapKeys.AUTH_USER_INFO.getKeyName(),
        ResourceKeys.CLONING_INSTRUCTIONS,
        ControlledResourceKeys.DESTINATION_RESOURCE_ID,
        ControlledResourceKeys.DESTINATION_WORKSPACE_ID);

    FlightBeanBag flightBeanBag = FlightBeanBag.getFromObject(applicationContext);
    var sourceResource = inputParameters.get(ResourceKeys.RESOURCE, ControlledResource.class);
    var userRequest =
        inputParameters.get(JobMapKeys.AUTH_USER_INFO.getKeyName(), AuthenticatedUserRequest.class);
    var destinationWorkspaceId =
        inputParameters.get(ControlledResourceKeys.DESTINATION_WORKSPACE_ID, UUID.class);
    var destLocation = inputParameters.get(ControlledResourceKeys.LOCATION, String.class);

    boolean mergePolicies =
        Optional.ofNullable(
                inputParameters.get(WorkspaceFlightMapKeys.MERGE_POLICIES, Boolean.class))
            .orElse(false);
    CloningInstructions resolvedCloningInstructions =
        Optional.ofNullable(
                inputParameters.get(ResourceKeys.CLONING_INSTRUCTIONS, CloningInstructions.class))
            .orElse(sourceResource.getCloningInstructions());

    if (CloningInstructions.COPY_NOTHING == resolvedCloningInstructions) {
      addStep(
          new SetNoOpBucketCloneResponseStep(
              sourceResource.castByEnum(WsmResourceType.CONTROLLED_GCP_BIG_QUERY_DATASET)));
      return;
    }

    // Flight Plan
    // 1. Validate user has read access to the source object
    // 2. Gather controlled resource metadata for source object
    // 3. If cloning to referenced resource, do the clone and finish flight
    // 4. Launch sub-flight to create destination controlled resource
    addStep(
        new CheckControlledResourceAuthStep(
            sourceResource, flightBeanBag.getControlledResourceMetadataManager(), userRequest),
        RetryRules.shortExponential());
    if (mergePolicies) {
      addStep(
          new MergePolicyAttributesDryRunStep(
              sourceResource.getWorkspaceId(),
              destinationWorkspaceId,
              resolvedCloningInstructions,
              flightBeanBag.getTpsApiDispatch()));

      addStep(
          new ValidateWorkspaceAgainstPolicyStep(
              destinationWorkspaceId,
              sourceResource.getResourceType().getCloudPlatform(),
              destLocation,
              userRequest,
              flightBeanBag.getResourceDao(),
              flightBeanBag.getTpsApiDispatch()));

      addStep(
          new MergePolicyAttributesStep(
              sourceResource.getWorkspaceId(),
              destinationWorkspaceId,
              resolvedCloningInstructions,
              flightBeanBag.getTpsApiDispatch()));
    }
    addStep(
        new RetrieveControlledResourceMetadataStep(
            flightBeanBag.getResourceDao(),
            sourceResource.getWorkspaceId(),
            sourceResource.getResourceId()));

    final ControlledBigQueryDatasetResource sourceDataset =
        sourceResource.castByEnum(WsmResourceType.CONTROLLED_GCP_BIG_QUERY_DATASET);

    if (CloningInstructions.COPY_REFERENCE == resolvedCloningInstructions
        || CloningInstructions.LINK_REFERENCE == resolvedCloningInstructions) {
      // Destination dataset is referenced resource
      addStep(
          new SetReferencedDestinationBigQueryDatasetInWorkingMapStep(
              flightBeanBag.getSamService(),
              userRequest,
              sourceDataset,
              flightBeanBag.getReferencedResourceService(),
              resolvedCloningInstructions),
          RetryRules.shortExponential());
      addStep(
          new CreateReferenceMetadataStep(
              userRequest, flightBeanBag.getReferencedResourceService()),
          RetryRules.shortDatabase());
      addStep(
          new SetReferencedDestinationBigQueryDatasetResponseStep(
              flightBeanBag.getResourceDao(), flightBeanBag.getWorkspaceActivityLogService()),
          RetryRules.shortExponential());
      return;
    }
    if (CloningInstructions.COPY_DEFINITION == resolvedCloningInstructions
        || CloningInstructions.COPY_RESOURCE == resolvedCloningInstructions) {
      // Destination dataset is controlled resource
      addStep(
          new CopyBigQueryDatasetDefinitionStep(
              flightBeanBag.getSamService(),
              sourceDataset,
              flightBeanBag.getControlledResourceService(),
              userRequest,
              flightBeanBag.getGcpCloudContextService(),
              resolvedCloningInstructions,
              flightBeanBag.getWorkspaceActivityLogService()));
      if (CloningInstructions.COPY_RESOURCE == resolvedCloningInstructions) {
        // Use the BigQuery Data Transfer API for cross-region dataset copies. For table copy jobs,
        // the destination dataset must reside in the same location as the dataset containing the
        // table being copied.
        // https://cloud.google.com/bigquery/docs/managing-tables#limitations_on_copying_tables
        if (destLocation != null && !(destLocation.equals(sourceResource.getRegion()))) {
          addStep(
              new CopyBigQueryDatasetDifferentRegionStep(
                  flightBeanBag.getSamService(),
                  sourceDataset,
                  userRequest,
                  flightBeanBag.getGcpCloudContextService()));
        } else {
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
    } else {
      throw new IllegalArgumentException(
          String.format(
              "Cloning Instructions %s not supported", resolvedCloningInstructions.toString()));
    }
  }
}
