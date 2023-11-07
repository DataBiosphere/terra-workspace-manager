package bio.terra.workspace.service.resource.controlled.flight.clone.azure.common;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.service.resource.controlled.model.ControlledResource;
import bio.terra.workspace.service.resource.model.CloningInstructions;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;

public class SetNoOpResourceCloneResponseStep implements Step {
  private final ControlledResource sourceResource;

  public SetNoOpResourceCloneResponseStep(ControlledResource sourceResource) {
    this.sourceResource = sourceResource;
  }

  @Override
  public StepResult doStep(FlightContext context) throws InterruptedException, RetryException {
    var noopResult =
        new ClonedCopyNothingResource(
            CloningInstructions.COPY_NOTHING,
            sourceResource.getWorkspaceId(),
            sourceResource.getResourceId());

    context
        .getWorkingMap()
        .put(WorkspaceFlightMapKeys.ControlledResourceKeys.CLONED_RESOURCE, noopResult);
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    return StepResult.getStepResultSuccess();
  }
}
