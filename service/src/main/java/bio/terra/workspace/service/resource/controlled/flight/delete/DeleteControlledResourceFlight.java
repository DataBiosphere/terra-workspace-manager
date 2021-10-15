package bio.terra.workspace.service.resource.controlled.flight.delete;

import bio.terra.stairway.Flight;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.RetryRule;
import bio.terra.stairway.RetryRuleFixedInterval;
import bio.terra.workspace.common.utils.FlightBeanBag;
import bio.terra.workspace.common.utils.RetryRules;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.job.JobMapKeys;
import bio.terra.workspace.service.resource.controlled.ControlledResource;
import bio.terra.workspace.service.resource.controlled.exception.ControlledResourceNotImplementedException;
import bio.terra.workspace.service.resource.controlled.flight.delete.notebook.DeleteAiNotebookInstanceStep;
import bio.terra.workspace.service.resource.controlled.flight.delete.notebook.DeleteServiceAccountStep;
import bio.terra.workspace.service.resource.controlled.flight.delete.notebook.RetrieveNotebookServiceAccountStep;
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
    // 1. Delete the Sam resource. That will make the object inaccessible.
    // 2. Delete the cloud resource. This has unique logic for each resource type. Depending on the
    // specifics of the resource type, this step may require the flight to run asynchronously.
    // 3. Delete the metadata
    /* intervalSeconds= */
    /* maxCount=  */ final RetryRule samRetryRule =
        new RetryRuleFixedInterval(/* intervalSeconds= */ 10, /* maxCount=  */ 2);
    addStep(
        new DeleteSamResourceStep(
            flightBeanBag.getResourceDao(),
            flightBeanBag.getSamService(),
            workspaceId,
            resourceId,
            userRequest),
        samRetryRule);

    final RetryRule gcpRetryRule = RetryRules.cloud();
    switch (resource.getResourceType()) {
      case GCS_BUCKET:
        addStep(
            new DeleteGcsBucketStep(
                flightBeanBag.getCrlService(),
                flightBeanBag.getResourceDao(),
                flightBeanBag.getGcpCloudContextService(),
                workspaceId,
                resourceId));
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
            new RetrieveNotebookServiceAccountStep(
                resource.castToAiNotebookInstanceResource(),
                flightBeanBag.getCrlService(),
                flightBeanBag.getGcpCloudContextService()),
            gcpRetryRule);
        addStep(
            new DeleteAiNotebookInstanceStep(
                resource.castToAiNotebookInstanceResource(),
                flightBeanBag.getCrlService(),
                flightBeanBag.getGcpCloudContextService()),
            gcpRetryRule);
        addStep(
            new DeleteServiceAccountStep(
                resource.castToAiNotebookInstanceResource(),
                flightBeanBag.getCrlService(),
                flightBeanBag.getGcpCloudContextService()),
            gcpRetryRule);
        break;
      default:
        throw new ControlledResourceNotImplementedException(
            "Delete not yet implemented for resource type " + resource.getResourceType());
    }

    final RetryRule immediateRetryRule =
        new RetryRuleFixedInterval(/*intervalSeconds= */ 0, /* maxCount= */ 2);
    addStep(
        new DeleteMetadataStep(flightBeanBag.getResourceDao(), workspaceId, resourceId),
        immediateRetryRule);
  }
}
