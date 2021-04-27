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
  private final RetryRule syncCloudRetryRule =
      new RetryRuleFixedInterval(/* intervalSeconds= */ 10, /* maxCount=  */ 10);

  public DeleteControlledResourceFlight(FlightMap inputParameters, Object beanBag) {
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
            userRequest));

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
            syncCloudRetryRule);
        break;
      case AI_NOTEBOOK_INSTANCE:
      default:
        throw new ControlledResourceNotImplementedException(
            "Delete not yet implemented for resource type " + resource.getResourceType());
    }

    addStep(new DeleteMetadataStep(flightBeanBag.getResourceDao(), workspaceId, resourceId));
  }
}
