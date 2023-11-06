package bio.terra.workspace.service.resource.controlled.flight.clone.azure.common;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.common.utils.FlightUtils;
import bio.terra.workspace.service.resource.controlled.model.ControlledResource;
import bio.terra.workspace.service.resource.model.CloningInstructions;
import org.springframework.http.HttpStatus;

public class SetNoOpResourceCloneResponseStep<T extends ControlledResource> implements Step {
  private final T sourceResource;

  public SetNoOpResourceCloneResponseStep(T sourceResource) {
    this.sourceResource = sourceResource;
  }

  @Override
  public StepResult doStep(FlightContext context) throws InterruptedException, RetryException {
    var noopResult =
        new ClonedCopyNothingResource(
            CloningInstructions.COPY_NOTHING,
            sourceResource.getWorkspaceId(),
            sourceResource.getResourceId());

    FlightUtils.setResponse(context, noopResult, HttpStatus.OK);
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    return StepResult.getStepResultSuccess();
  }
}
