package bio.terra.workspace.service.policy.flight;

import bio.terra.policy.model.TpsComponent;
import bio.terra.policy.model.TpsObjectType;
import bio.terra.policy.model.TpsPaoUpdateResult;
import bio.terra.policy.model.TpsUpdateMode;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.service.policy.TpsApiDispatch;
import bio.terra.workspace.service.resource.exception.PolicyConflictException;
import bio.terra.workspace.service.resource.model.CloningInstructions;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MergePolicyAttributesDryRunStep implements Step {
  private static final Logger logger =
      LoggerFactory.getLogger(MergePolicyAttributesDryRunStep.class);

  private final UUID sourceWorkspaceId;
  private final UUID destinationWorkspaceId;
  private final CloningInstructions cloningInstructions;
  private final TpsApiDispatch tpsApiDispatch;

  public MergePolicyAttributesDryRunStep(
      UUID sourceWorkspaceId,
      UUID destinationWorkspaceId,
      CloningInstructions cloningInstructions,
      TpsApiDispatch tpsApiDispatch) {
    this.sourceWorkspaceId = sourceWorkspaceId;
    this.destinationWorkspaceId = destinationWorkspaceId;
    this.tpsApiDispatch = tpsApiDispatch;
    this.cloningInstructions = cloningInstructions;
  }

  @Override
  public StepResult doStep(FlightContext flightContext)
      throws InterruptedException, RetryException {
    // Create PAOs if they don't exist; catch TPS exceptions and retry
    try {
      tpsApiDispatch.createPaoIfNotExist(
          sourceWorkspaceId, TpsComponent.WSM, TpsObjectType.WORKSPACE);
      tpsApiDispatch.createPaoIfNotExist(
          destinationWorkspaceId, TpsComponent.WSM, TpsObjectType.WORKSPACE);
    } catch (Exception ex) {
      logger.info("Attempt to create a PAO for workspace failed", ex);
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY, ex);
    }

    TpsPaoUpdateResult dryRunResults =
        (cloningInstructions == CloningInstructions.LINK_REFERENCE)
            ? tpsApiDispatch.linkPao(
                destinationWorkspaceId, sourceWorkspaceId, TpsUpdateMode.DRY_RUN)
            : tpsApiDispatch.mergePao(
                sourceWorkspaceId, destinationWorkspaceId, TpsUpdateMode.DRY_RUN);

    if (!dryRunResults.getConflicts().isEmpty()) {
      throw new PolicyConflictException("Policy merge has conflicts", dryRunResults.getConflicts());
    }

    flightContext
        .getWorkingMap()
        .put(WorkspaceFlightMapKeys.EFFECTIVE_POLICIES, dryRunResults.getResultingPao());
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    // Since this was a dry run, there should be nothing to undo
    return StepResult.getStepResultSuccess();
  }
}
