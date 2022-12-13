package bio.terra.workspace.service.workspace.flight;

import bio.terra.policy.model.TpsPolicyInputs;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.policy.TpsApiDispatch;
import bio.terra.workspace.service.policy.exception.PolicyServiceDuplicateException;
import bio.terra.workspace.service.workspace.model.Workspace;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CreateWorkspacePoliciesStep implements Step {

  private static final Logger logger = LoggerFactory.getLogger(CreateWorkspacePoliciesStep.class);
  private final Workspace workspace;
  @Nullable private final TpsPolicyInputs policyInputs;
  private final TpsApiDispatch tpsApiDispatch;
  private final AuthenticatedUserRequest userRequest;

  public CreateWorkspacePoliciesStep(
      Workspace workspace,
      @Nullable TpsPolicyInputs policyInputs,
      TpsApiDispatch tpsApiDispatch,
      AuthenticatedUserRequest userRequest) {
    this.workspace = workspace;
    this.policyInputs = policyInputs;
    this.tpsApiDispatch = tpsApiDispatch;
    this.userRequest = userRequest;
  }

  @Override
  public StepResult doStep(FlightContext context) throws InterruptedException, RetryException {
    try {
        tpsApiDispatch.createPao(workspace.getWorkspaceId(), policyInputs);
    } catch (PolicyServiceDuplicateException e) {
      // Before the flight we check that the workspace does not exist, so it's safe to assume that
      // any policy on this workspace object was created by this flight, and we can ignore
      // duplicates.
      logger.info(
          "Created duplicate policy for workspace {}. This is expected for Stairway retries",
          workspace.getWorkspaceId());
    }
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    // Before the flight we check that the workspace does not exist, so it's
    // safe to assume that any policy on this workspace object was created by this flight.
    // deletePao does not throw if the policy object is missing, so this operation is idempotent.
    tpsApiDispatch.deletePao(workspace.getWorkspaceId());
    return StepResult.getStepResultSuccess();
  }
}
