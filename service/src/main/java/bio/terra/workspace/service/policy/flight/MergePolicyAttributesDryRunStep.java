package bio.terra.workspace.service.policy.flight;

import bio.terra.policy.model.TpsPaoUpdateResult;
import bio.terra.policy.model.TpsUpdateMode;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.policy.TpsApiDispatch;
import bio.terra.workspace.service.resource.exception.PolicyConflictException;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MergePolicyAttributesDryRunStep implements Step {
  private static final Logger logger =
      LoggerFactory.getLogger(MergePolicyAttributesDryRunStep.class);

  private final UUID sourceWorkspaceId;
  private final UUID destinationWorkspaceId;
  private final AuthenticatedUserRequest userRequest;
  private final TpsApiDispatch tpsApiDispatch;

  public MergePolicyAttributesDryRunStep(
      UUID sourceWorkspaceId,
      UUID destinationWorkspaceId,
      AuthenticatedUserRequest userRequest,
      TpsApiDispatch tpsApiDispatch) {
    this.sourceWorkspaceId = sourceWorkspaceId;
    this.destinationWorkspaceId = destinationWorkspaceId;
    this.userRequest = userRequest;
    this.tpsApiDispatch = tpsApiDispatch;
  }

  @Override
  public StepResult doStep(FlightContext flightContext)
      throws InterruptedException, RetryException {
    // Create PAOs if they don't exist; catch TPS exceptions and retry
    try {
      MergePolicyAttributesUtils.createPaoIfNotExist(tpsApiDispatch, sourceWorkspaceId);
      MergePolicyAttributesUtils.createPaoIfNotExist(tpsApiDispatch, destinationWorkspaceId);
    } catch (Exception ex) {
      logger.info("Attempt to create a PAO for workspace failed", ex);
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY, ex);
    }

    TpsPaoUpdateResult dryRunResults =
        tpsApiDispatch.mergePao(destinationWorkspaceId, sourceWorkspaceId, TpsUpdateMode.DRY_RUN);

    if (!dryRunResults.getConflicts().isEmpty()) {
      List<String> conflictList =
          dryRunResults.getConflicts().stream()
              .map(c -> c.getNamespace() + ':' + c.getName())
              .toList();
      throw new PolicyConflictException("Policy merge has conflicts", conflictList);
    }

    flightContext
        .getWorkingMap()
        .put(WorkspaceFlightMapKeys.EFFECTIVE_POLICIES, dryRunResults.getResultingPao());
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    // Since this was a dry run, there should be nothing to undo, only propagate the flight failure.
    return context.getResult();
  }
}
