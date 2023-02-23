package bio.terra.workspace.service.policy.flight;

import bio.terra.common.iam.BearerToken;
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
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.policy.TpsApiDispatch;
import bio.terra.workspace.service.resource.exception.PolicyConflictException;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LinkPolicyAttributesStep implements Step {
  private static final Logger logger = LoggerFactory.getLogger(MergePolicyAttributesStep.class);

  private final UUID sourceWorkspaceId;
  private final UUID destinationWorkspaceId;
  private final AuthenticatedUserRequest userRequest;
  private final TpsApiDispatch tpsApiDispatch;

  public LinkPolicyAttributesStep(
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
  public StepResult doStep(FlightContext context) throws InterruptedException, RetryException {
    // Create PAOs if they don't exist; catch TPS exceptions and retry
    try {
      tpsApiDispatch.createPaoIfNotExist(sourceWorkspaceId);
      tpsApiDispatch.createPaoIfNotExist(destinationWorkspaceId);
    } catch (Exception ex) {
      logger.info("Attempt to create a PAO for workspace failed", ex);
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY, ex);
    }

    // Save the destination attributes so we can restore them if the flight fails
    TpsPaoGetResult destinationPao = tpsApiDispatch.getPao(destinationWorkspaceId);
    TpsPolicyInputs destinationAttributes = destinationPao.getAttributes();
    context.getWorkingMap().put(WorkspaceFlightMapKeys.POLICIES, destinationAttributes);

    TpsPaoUpdateResult result =
        tpsApiDispatch.linkPao(
            sourceWorkspaceId, destinationWorkspaceId, TpsUpdateMode.FAIL_ON_CONFLICT);
    if (!result.isUpdateApplied()) {
      List<String> conflictList =
          result.getConflicts().stream().map(c -> c.getNamespace() + ':' + c.getName()).toList();
      throw new PolicyConflictException(
          "Destination workspace policies conflict with source workspace policies", conflictList);
    }

    context
        .getWorkingMap()
        .put(WorkspaceFlightMapKeys.EFFECTIVE_POLICIES, result.getResultingPao());
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    BearerToken token = new BearerToken(userRequest.getRequiredToken());
    var destinationAttributes =
        context.getWorkingMap().get(WorkspaceFlightMapKeys.POLICIES, TpsPolicyInputs.class);

    // If the working map didn't get populated, we failed before the merge, so
    // consider it undone.
    if (destinationAttributes == null) {
      return StepResult.getStepResultSuccess();
    }

    TpsPaoUpdateResult result =
        tpsApiDispatch.replacePao(
            destinationWorkspaceId, destinationAttributes, TpsUpdateMode.FAIL_ON_CONFLICT);
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
