package bio.terra.workspace.service.resource.controlled.flight.delete;

import bio.terra.stairway.Flight;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.RetryRule;
import bio.terra.workspace.common.utils.FlightBeanBag;
import bio.terra.workspace.common.utils.RetryRules;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.job.JobMapKeys;
import bio.terra.workspace.service.resource.controlled.cloud.azure.disk.DeleteAzureDiskStep;
import bio.terra.workspace.service.resource.controlled.cloud.azure.ip.DeleteAzureIpStep;
import bio.terra.workspace.service.resource.controlled.cloud.azure.network.DeleteAzureNetworkStep;
import bio.terra.workspace.service.resource.controlled.cloud.azure.vm.DeleteAzureVmStep;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.ainotebook.DeleteAiNotebookInstanceStep;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.bqdataset.DeleteBigQueryDatasetStep;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.gcsbucket.DeleteGcsBucketStep;
import bio.terra.workspace.service.resource.controlled.exception.ControlledResourceNotImplementedException;
import bio.terra.workspace.service.resource.controlled.model.ControlledResource;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import java.util.UUID;

/**
 * Flight for type-agnostic deletion of a controlled resource. All type-specific information should
 * live in individual steps.
 */
public class DeleteControlledResourceFlight extends Flight {

  public DeleteControlledResourceFlight(FlightMap inputParameters, Object beanBag)
      throws InterruptedException {
    super(inputParameters, beanBag);
    final FlightBeanBag flightBeanBag = FlightBeanBag.getFromObject(beanBag);

    final UUID workspaceId =
        UUID.fromString(inputParameters.get(WorkspaceFlightMapKeys.WORKSPACE_ID, String.class));
    final UUID resourceId =
        UUID.fromString(
            inputParameters.get(WorkspaceFlightMapKeys.ResourceKeys.RESOURCE_ID, String.class));
    final AuthenticatedUserRequest userRequest =
        inputParameters.get(JobMapKeys.AUTH_USER_INFO.getKeyName(), AuthenticatedUserRequest.class);
    ControlledResource resource =
        flightBeanBag
            .getResourceDao()
            .getResource(workspaceId, resourceId)
            .castToControlledResource();

    // Flight plan:
    // 1. Delete the cloud resource. This has unique logic for each resource type. Depending on the
    // specifics of the resource type, this step may require the flight to run asynchronously.
    // 2. Delete the Sam resource. That will make the object inaccessible.
    // 3. Delete the metadata

    final RetryRule gcpRetryRule = RetryRules.cloud();
    switch (resource.getResourceType()) {
      case GCS_BUCKET:
        addStep(
            new DeleteGcsBucketStep(
                flightBeanBag.getCrlService(),
                flightBeanBag.getResourceDao(),
                flightBeanBag.getGcpCloudContextService(),
                workspaceId,
                resourceId),
            gcpRetryRule);
        break;
      case AZURE_DISK:
        addStep(
            new DeleteAzureDiskStep(
                flightBeanBag.getAzureConfig(),
                flightBeanBag
                    .getAzureCloudContextService()
                    .getAzureCloudContext(resource.getWorkspaceId())
                    .get(),
                flightBeanBag.getCrlService(),
                flightBeanBag.getResourceDao(),
                workspaceId,
                resourceId));
        break;
      case AZURE_IP:
        addStep(
            new DeleteAzureIpStep(
                flightBeanBag.getAzureConfig(),
                flightBeanBag
                    .getAzureCloudContextService()
                    .getAzureCloudContext(resource.getWorkspaceId())
                    .get(),
                flightBeanBag.getCrlService(),
                flightBeanBag.getResourceDao(),
                workspaceId,
                resourceId));
        break;
      case AZURE_NETWORK:
        addStep(
            new DeleteAzureNetworkStep(
                flightBeanBag.getAzureConfig(),
                flightBeanBag
                    .getAzureCloudContextService()
                    .getAzureCloudContext(resource.getWorkspaceId())
                    .get(),
                flightBeanBag.getCrlService(),
                flightBeanBag.getResourceDao(),
                workspaceId,
                resourceId));
        break;
      case AZURE_VM:
        addStep(
            new DeleteAzureVmStep(
                flightBeanBag.getAzureConfig(),
                flightBeanBag
                    .getAzureCloudContextService()
                    .getAzureCloudContext(resource.getWorkspaceId())
                    .get(),
                flightBeanBag.getCrlService(),
                flightBeanBag.getResourceDao(),
                workspaceId,
                resourceId));
        break;
      case BIG_QUERY_DATASET:
        addStep(
            new DeleteBigQueryDatasetStep(
                resource.castToBigQueryDatasetResource(),
                flightBeanBag.getCrlService(),
                flightBeanBag.getGcpCloudContextService()),
            gcpRetryRule);
        break;
      case AI_NOTEBOOK_INSTANCE:
        addStep(
            new DeleteAiNotebookInstanceStep(
                resource.castToAiNotebookInstanceResource(),
                flightBeanBag.getCrlService(),
                flightBeanBag.getGcpCloudContextService()),
            gcpRetryRule);
        break;
      default:
        throw new ControlledResourceNotImplementedException(
            "Delete not yet implemented for resource type " + resource.getResourceType());
    }
    addStep(
        new DeleteSamResourceStep(
            flightBeanBag.getResourceDao(),
            flightBeanBag.getSamService(),
            workspaceId,
            resourceId,
            userRequest));
    addStep(
        new DeleteMetadataStep(flightBeanBag.getResourceDao(), workspaceId, resourceId),
        RetryRules.shortDatabase());
  }
}
