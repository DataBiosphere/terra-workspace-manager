package bio.terra.workspace.service.resource.controlled.flight.clone.bucket;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.common.utils.FlightUtils;
import bio.terra.workspace.generated.model.ApiClonedControlledGcpGcsBucket;
import bio.terra.workspace.generated.model.ApiCloningInstructionsEnum;
import bio.terra.workspace.service.resource.controlled.cloud.gcp.gcsbucket.ControlledGcsBucketResource;
import org.springframework.http.HttpStatus;

/**
 * If cloning instructions say COPY_NOTHING, we need to stub the response object and add it to the
 * flight context.
 *
 * <p>Preconditions: Source bucket exists. Resolved cloning instructions are COPY_NOTHING. Post
 * conditions: Response is set on flight contexts to a no-op structure.
 */
public class SetNoOpBucketCloneResponseStep implements Step {

  private final ControlledGcsBucketResource sourceBucket;

  public SetNoOpBucketCloneResponseStep(ControlledGcsBucketResource sourceBucket) {
    this.sourceBucket = sourceBucket;
  }

  @Override
  public StepResult doStep(FlightContext context) throws InterruptedException, RetryException {
    ApiClonedControlledGcpGcsBucket noOpResult =
        new ApiClonedControlledGcpGcsBucket()
            .effectiveCloningInstructions(ApiCloningInstructionsEnum.NOTHING)
            .bucket(null)
            .sourceWorkspaceId(sourceBucket.getWorkspaceId())
            .sourceResourceId(sourceBucket.getResourceId());
    FlightUtils.setResponse(context, noOpResult, HttpStatus.OK);

    return StepResult.getStepResultSuccess();
  }

  // No side effects to undo.
  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    return StepResult.getStepResultSuccess();
  }
}
