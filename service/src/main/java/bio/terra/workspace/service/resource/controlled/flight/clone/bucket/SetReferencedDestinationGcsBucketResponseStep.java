package bio.terra.workspace.service.resource.controlled.flight.clone.bucket;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.common.utils.FlightUtils;
import bio.terra.workspace.db.ResourceDao;
import bio.terra.workspace.generated.model.ApiClonedControlledGcpGcsBucket;
import bio.terra.workspace.generated.model.ApiCloningInstructionsEnum;
import bio.terra.workspace.generated.model.ApiCreatedControlledGcpGcsBucket;
import bio.terra.workspace.service.logging.WorkspaceActivityLogService;
import bio.terra.workspace.service.resource.controlled.model.ControlledResource;
import bio.terra.workspace.service.resource.model.WsmResourceType;
import bio.terra.workspace.service.resource.referenced.cloud.gcp.gcsbucket.ReferencedGcsBucketResource;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ResourceKeys;
import java.util.UUID;
import org.springframework.http.HttpStatus;

/**
 * Sets flight response to destination bucket.
 *
 * <p>Note - This can't be done in SetReferencedDestinationGcsBucketInWorkingMapStep, because
 * CreateReferenceMetadataStep sets flight response to dest resource ID. So this must be done after
 * CreateReferenceMetadataStep.
 */
public class SetReferencedDestinationGcsBucketResponseStep implements Step {
  private final ResourceDao resourceDao;
  private final WorkspaceActivityLogService workspaceActivityLogService;

  public SetReferencedDestinationGcsBucketResponseStep(
      ResourceDao resourceDao, WorkspaceActivityLogService workspaceActivityLogService) {
    this.resourceDao = resourceDao;
    this.workspaceActivityLogService = workspaceActivityLogService;
  }

  @Override
  public StepResult doStep(FlightContext context) throws InterruptedException, RetryException {
    FlightUtils.validateRequiredEntries(
        context.getInputParameters(),
        ControlledResourceKeys.DESTINATION_WORKSPACE_ID,
        ControlledResourceKeys.DESTINATION_RESOURCE_ID);

    UUID destWorkspaceId =
        context
            .getInputParameters()
            .get(ControlledResourceKeys.DESTINATION_WORKSPACE_ID, UUID.class);
    UUID destResourceId =
        context
            .getInputParameters()
            .get(ControlledResourceKeys.DESTINATION_RESOURCE_ID, UUID.class);
    ControlledResource sourceBucket =
        context.getInputParameters().get(ResourceKeys.RESOURCE, ControlledResource.class);

    // ControlledResourceKeys.DESTINATION_REFERENCED_RESOURCE contains destination bucket. However,
    // that does not have createdDate set. So reread from dao, which will have createdDate.
    ReferencedGcsBucketResource destBucket =
        resourceDao
            .getResource(destWorkspaceId, destResourceId)
            .castByEnum(WsmResourceType.REFERENCED_GCP_GCS_BUCKET);

    ApiCreatedControlledGcpGcsBucket apiCreatedBucket =
        new ApiCreatedControlledGcpGcsBucket()
            .gcpBucket(destBucket.toApiResource())
            .resourceId(destBucket.getResourceId());

    final ApiClonedControlledGcpGcsBucket apiClonedBucket =
        new ApiClonedControlledGcpGcsBucket()
            .effectiveCloningInstructions(ApiCloningInstructionsEnum.REFERENCE)
            .bucket(apiCreatedBucket)
            .sourceWorkspaceId(sourceBucket.getWorkspaceId())
            .sourceResourceId(sourceBucket.getResourceId());
    FlightUtils.setResponse(context, apiClonedBucket, HttpStatus.OK);

    return StepResult.getStepResultSuccess();
  }

  // No side effects to undo.
  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    return StepResult.getStepResultSuccess();
  }
}
