package bio.terra.workspace.service.policy.flight;

import bio.terra.policy.model.TpsPaoGetResult;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.service.policy.TpsApiDispatch;
import bio.terra.workspace.service.policy.TpsUtilities;
import bio.terra.workspace.service.resource.exception.PolicyConflictException;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import java.util.HashSet;
import java.util.UUID;

// TODO: Once we have support for group update, we can remove this class.
public class ValidateGroupPolicyAttributesStep implements Step {
  private final UUID workspaceId;
  private final TpsApiDispatch tpsApiDispatch;

  public ValidateGroupPolicyAttributesStep(UUID workspaceId, TpsApiDispatch tpsApiDispatch) {
    this.workspaceId = workspaceId;
    this.tpsApiDispatch = tpsApiDispatch;
  }

  @Override
  public StepResult doStep(FlightContext flightContext)
      throws InterruptedException, RetryException {
    TpsPaoGetResult mergedPao =
        flightContext
            .getWorkingMap()
            .get(WorkspaceFlightMapKeys.EFFECTIVE_POLICIES, TpsPaoGetResult.class);

    // For Milestone 1, we aren't able to change groups. So if the effectivePolices have different
    // groups than the existing workspace, we'll fail. Otherwise, the step will succeed.

    TpsPaoGetResult currentPao = tpsApiDispatch.getPao(workspaceId);

    HashSet<String> currentGroup =
        new HashSet<>(
            TpsUtilities.getGroupConstraintsFromInputs(currentPao.getEffectiveAttributes()));
    HashSet<String> mergedGroup =
        new HashSet<>(
            TpsUtilities.getGroupConstraintsFromInputs(mergedPao.getEffectiveAttributes()));

    if (currentGroup.equals(mergedGroup)) {
      return StepResult.getStepResultSuccess();
    }

    throw new PolicyConflictException("Cannot update group policies.");
  }

  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    // Validation step so there should be nothing to undo, only propagate the flight failure.
    return StepResult.getStepResultSuccess();
  }
}
