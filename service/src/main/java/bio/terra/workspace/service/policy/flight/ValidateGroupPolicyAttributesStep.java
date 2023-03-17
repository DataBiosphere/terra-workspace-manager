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
    final TpsPaoGetResult mergedPao =
        flightContext
            .getWorkingMap()
            .get(WorkspaceFlightMapKeys.EFFECTIVE_POLICIES, TpsPaoGetResult.class);

    // For Milestone 1, we aren't able to change groups. So if the effectivePolices have different
    // groups than the existing workspace, we'll fail. Otherwise, the step will succeed.

    final TpsPaoGetResult currentPao = tpsApiDispatch.getPao(workspaceId);

    HashSet<String> groups1 =
        new HashSet<>(
            TpsUtilities.getGroupConstraintsFromInputs(currentPao.getEffectiveAttributes()));
    HashSet<String> groups2 =
        new HashSet<>(
            TpsUtilities.getGroupConstraintsFromInputs(mergedPao.getEffectiveAttributes()));

    if (!(groups1.containsAll(groups2) && groups2.containsAll(groups1))) {
      throw new PolicyConflictException("Cannot update group policies.");
    }

    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    // Validation step so there should be nothing to undo, only propagate the flight failure.
    return context.getResult();
  }
}
