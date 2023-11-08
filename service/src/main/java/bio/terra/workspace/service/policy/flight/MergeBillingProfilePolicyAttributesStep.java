package bio.terra.workspace.service.policy.flight;

import bio.terra.policy.model.TpsComponent;
import bio.terra.policy.model.TpsObjectType;
import bio.terra.policy.model.TpsPaoConflict;
import bio.terra.policy.model.TpsPaoGetResult;
import bio.terra.policy.model.TpsPaoUpdateResult;
import bio.terra.policy.model.TpsPolicyInputs;
import bio.terra.policy.model.TpsUpdateMode;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.common.exception.InternalLogicException;
import bio.terra.workspace.service.policy.TpsApiDispatch;
import bio.terra.workspace.service.resource.exception.PolicyConflictException;
import bio.terra.workspace.service.spendprofile.SpendProfileId;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MergeBillingProfilePolicyAttributesStep implements Step {
  private static final Logger logger =
      LoggerFactory.getLogger(MergeBillingProfilePolicyAttributesStep.class);

  private final UUID workspaceId;
  private final SpendProfileId spendProfileId;
  private final TpsApiDispatch tpsApiDispatch;

  public MergeBillingProfilePolicyAttributesStep(
      UUID workspaceId, SpendProfileId spendProfileId, TpsApiDispatch tpsApiDispatch) {
    this.workspaceId = workspaceId;
    this.spendProfileId = spendProfileId;
    this.tpsApiDispatch = tpsApiDispatch;
  }

  @Override
  public StepResult doStep(FlightContext context) throws InterruptedException, RetryException {
    // Create PAOs if they don't exist; catch TPS exceptions and retry
    TpsPaoGetResult destinationPao;
    UUID spendProfileUUID = UUID.fromString(spendProfileId.getId());
    try {
      tpsApiDispatch.getOrCreatePao(
          spendProfileUUID, TpsComponent.BPM, TpsObjectType.BILLING_PROFILE);
      destinationPao =
          tpsApiDispatch.getOrCreatePao(workspaceId, TpsComponent.WSM, TpsObjectType.WORKSPACE);
    } catch (Exception ex) {
      logger.info(
          "Attempt to create a PAO for billing profile or destination workspace failed", ex);
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY, ex);
    }

    // Save the destination attributes so that we can restore them if the flight fails
    TpsPolicyInputs destinationAttributes = destinationPao.getAttributes();
    context.getWorkingMap().put(WorkspaceFlightMapKeys.POLICIES, destinationAttributes);

    TpsPaoUpdateResult result =
        tpsApiDispatch.mergePao(workspaceId, spendProfileUUID, TpsUpdateMode.FAIL_ON_CONFLICT);
    if (!result.isUpdateApplied()) {
      for (TpsPaoConflict conflict : result.getConflicts()) {
        logger.info("Policy conflict: {}", conflict);
      }
      throw new PolicyConflictException(
          "Workspace policies conflict with billing profile policies", result.getConflicts());
    }

    context
        .getWorkingMap()
        .put(WorkspaceFlightMapKeys.EFFECTIVE_POLICIES, result.getResultingPao());
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    var destinationAttributes =
        context.getWorkingMap().get(WorkspaceFlightMapKeys.POLICIES, TpsPolicyInputs.class);

    // If the working map didn't get populated, we failed before the merge, so
    // consider it undone.
    if (destinationAttributes == null) {
      return StepResult.getStepResultSuccess();
    }

    TpsPaoUpdateResult result =
        tpsApiDispatch.replacePao(
            workspaceId, destinationAttributes, TpsUpdateMode.FAIL_ON_CONFLICT);
    if (!result.isUpdateApplied()) {
      List<String> conflictList =
          result.getConflicts().stream().map(c -> c.getNamespace() + ':' + c.getName()).toList();
      return new StepResult(
          StepStatus.STEP_RESULT_FAILURE_FATAL,
          new InternalLogicException(
              String.format("Failed to restore destination workspace policies: %s", conflictList)));
    }

    return StepResult.getStepResultSuccess();
  }
}
