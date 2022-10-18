package bio.terra.workspace.service.resource.controlled.flight.clone.bucket;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.common.utils.FlightUtils;
import bio.terra.workspace.generated.model.ApiClonedControlledGcpGcsBucket;
import bio.terra.workspace.generated.model.ApiCloningInstructionsEnum;
import bio.terra.workspace.generated.model.ApiCreatedControlledGcpGcsBucket;
import bio.terra.workspace.service.resource.controlled.model.ControlledResource;
import bio.terra.workspace.service.resource.referenced.cloud.gcp.gcsbucket.ReferencedGcsBucketResource;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ResourceKeys;
import org.springframework.http.HttpStatus;

/**
 * Sets flight response to destination bucket.
 *
 * <p>Note - This can't be done in SetReferencedDestinationGcsBucketInWorkingMapStep, because
 * CreateReferenceMetadataStep sets flight response to dest resource ID. So this must be done after
 * CreateReferenceMetadataStep.
 */
public class SetReferencedDestinationGcsBucketResponseStep implements Step {

  public SetReferencedDestinationGcsBucketResponseStep() {}

  @Override
  public StepResult doStep(FlightContext context) throws InterruptedException, RetryException {
    ControlledResource sourceBucket =
        context.getInputParameters().get(ResourceKeys.RESOURCE, ControlledResource.class);
    ReferencedGcsBucketResource destBucket =
        context
            .getWorkingMap()
            .get(
                ControlledResourceKeys.DESTINATION_REFERENCED_RESOURCE,
                ReferencedGcsBucketResource.class);

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
