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
import bio.terra.workspace.service.policy.exception.PolicyServiceDuplicateException;
import bio.terra.workspace.service.resource.exception.PolicyConflictException;
import bio.terra.workspace.service.spendprofile.SpendProfileId;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LinkSpendProfilePolicyAttributesStep implements Step {
  private static final Logger logger =
      LoggerFactory.getLogger(LinkSpendProfilePolicyAttributesStep.class);

  private final UUID workspaceId;
  private final SpendProfileId spendProfileId;
  private final TpsApiDispatch tpsApiDispatch;

  public LinkSpendProfilePolicyAttributesStep(
      UUID workspaceId, SpendProfileId spendProfileId, TpsApiDispatch tpsApiDispatch) {
    this.workspaceId = workspaceId;
    this.spendProfileId = spendProfileId;
    this.tpsApiDispatch = tpsApiDispatch;
  }

  @Override
  public StepResult doStep(FlightContext context) throws InterruptedException, RetryException {
    UUID spendProfileUUID;
    try {
      if (spendProfileId != null) {
        spendProfileUUID = UUID.fromString(spendProfileId.getId());
      } else {
        logger.info(
            "no spend profile id found, skipping spend profile and workspace PAO linking. [workspaceId={}]",
            workspaceId);
        return StepResult.getStepResultSuccess();
      }
    } catch (IllegalArgumentException e) {
      logger.info(
          "non-UUID spend profile id provided, skipping spend profile and workspace PAO linking. [spendProfileId={}, workspaceId={}]",
          spendProfileId,
          workspaceId);
      return StepResult.getStepResultSuccess();
    }

    // Create PAOs if they don't exist; catch TPS exceptions and retry
    TpsPaoGetResult workspacePao;
    try {
      tpsApiDispatch.getOrCreatePao(
          spendProfileUUID, TpsComponent.BPM, TpsObjectType.BILLING_PROFILE);
      workspacePao =
          tpsApiDispatch.getOrCreatePao(workspaceId, TpsComponent.WSM, TpsObjectType.WORKSPACE);
    } catch (PolicyServiceDuplicateException ex) {
      logger.info("Attempt to get a PAO for billing profile or workspace failed", ex);
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY, ex);
    }

    // Save the workspace policy attributes so that we can restore them if the flight fails
    TpsPolicyInputs destinationAttributes = workspacePao.getAttributes();
    context.getWorkingMap().put(WorkspaceFlightMapKeys.POLICIES, destinationAttributes);

    TpsPaoUpdateResult result =
        tpsApiDispatch.linkPao(workspaceId, spendProfileUUID, TpsUpdateMode.FAIL_ON_CONFLICT);

    if (!result.isUpdateApplied()) {
      for (TpsPaoConflict conflict : result.getConflicts()) {
        logger.info("Policy conflict: {}", conflict);
      }
      throw new PolicyConflictException(
          "Workspace policies conflict with billing profile policies", result.getConflicts());
    }

    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    var destinationAttributes =
        context.getWorkingMap().get(WorkspaceFlightMapKeys.POLICIES, TpsPolicyInputs.class);

    // If the working map didn't get populated, we failed before the link, so
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
