package bio.terra.workspace.service.resource.controlled.flight.delete;

import bio.terra.stairway.Flight;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.RetryRule;
import bio.terra.stairway.RetryRuleFixedInterval;
import bio.terra.workspace.common.utils.FlightBeanBag;
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
  /**
   * Retry rule for steps interacting with GCP. If GCP is down, we don't know when it will be back,
   * so don't wait forever. Note that RetryRules can be re-used within but not across Flight
   * instances.
   */
  private final RetryRule gcpRetryRule =
      new RetryRuleFixedInterval(/* intervalSeconds= */ 10, /* maxCount=  */ 2);

  /**
   * Retry rule for handling unexpected timeouts with Sam. Note that some errors from Sam (like
   * NOT_FOUND responses to resources which were already deleted) are not handled by retries.
   */
  private final RetryRule samRetryRule =
      new RetryRuleFixedInterval(/* intervalSeconds= */ 10, /* maxCount=  */ 2);

  /**
   * Retry rule for immediately retrying a failed step twice. This is useful for retrying operations
   * where waiting is not necessary, e.g. retrying conflicted SQL transactions.
   */
  private final RetryRule immediateRetryRule =
      new RetryRuleFixedInterval(/*intervalSeconds= */ 0, /* maxCount= */ 2);

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
    addStep(
        new DeleteSamResourceStep(
            flightBeanBag.getResourceDao(),
            flightBeanBag.getSamService(),
            workspaceId,
            resourceId,
            userRequest),
        samRetryRule);

    switch (resource.getResourceType()) {
      case GCS_BUCKET:
        addStep(
            new DeleteGcsBucketStep(
                flightBeanBag.getCrlService(),
                flightBeanBag.getResourceDao(),
                flightBeanBag.getWorkspaceService(),
                workspaceId,
                resourceId));
        break;
      case BIG_QUERY_DATASET:
        addStep(
            new DeleteBigQueryDatasetStep(
                resource.castToBigQueryDatasetResource(),
                flightBeanBag.getCrlService(),
                flightBeanBag.getWorkspaceService()),
            gcpRetryRule);
        break;
      case AI_NOTEBOOK_INSTANCE:
        addStep(
            new RetrieveNotebookServiceAccountStep(
                resource.castToAiNotebookInstanceResource(),
                flightBeanBag.getCrlService(),
                flightBeanBag.getWorkspaceService()),
            gcpRetryRule);
        addStep(
            new DeleteAiNotebookInstanceStep(
                resource.castToAiNotebookInstanceResource(),
                flightBeanBag.getCrlService(),
                flightBeanBag.getWorkspaceService()),
            gcpRetryRule);
        addStep(
            new DeleteServiceAccountStep(
                resource.castToAiNotebookInstanceResource(),
                flightBeanBag.getCrlService(),
                flightBeanBag.getWorkspaceService()),
            gcpRetryRule);
        break;
      default:
        throw new ControlledResourceNotImplementedException(
            "Delete not yet implemented for resource type " + resource.getResourceType());
    }

    addStep(
        new DeleteMetadataStep(flightBeanBag.getResourceDao(), workspaceId, resourceId),
        immediateRetryRule);
  }
}
