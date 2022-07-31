package bio.terra.workspace.service.workspace.flight;

import bio.terra.common.iam.BearerToken;
import bio.terra.policy.db.exception.DuplicateObjectException;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.amalgam.tps.TpsApiDispatch;
import bio.terra.workspace.generated.model.ApiTpsComponent;
import bio.terra.workspace.generated.model.ApiTpsObjectType;
import bio.terra.workspace.generated.model.ApiTpsPaoCreateRequest;
import bio.terra.workspace.generated.model.ApiTpsPolicyInputs;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.workspace.model.Workspace;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CreateWorkspacePoliciesStep implements Step {

  private static final Logger logger = LoggerFactory.getLogger(CreateWorkspacePoliciesStep.class);
  private final Workspace workspace;
  @Nullable private final ApiTpsPolicyInputs policyInputs;
  private final TpsApiDispatch tpsApiDispatch;
  private final AuthenticatedUserRequest userRequest;

  public CreateWorkspacePoliciesStep(
      Workspace workspace,
      ApiTpsPolicyInputs policyInputs,
      TpsApiDispatch tpsApiDispatch,
      AuthenticatedUserRequest userRequest) {
    this.workspace = workspace;
    this.policyInputs = policyInputs;
    this.tpsApiDispatch = tpsApiDispatch;
    this.userRequest = userRequest;
  }

  @Override
  public StepResult doStep(FlightContext context) throws InterruptedException, RetryException {
    ApiTpsPaoCreateRequest request =
        new ApiTpsPaoCreateRequest()
            .objectId(workspace.getWorkspaceId())
            .component(ApiTpsComponent.WSM)
            .objectType(ApiTpsObjectType.WORKSPACE)
            .attributes(
                new ApiTpsPolicyInputs()
                    .inputs(policyInputs == null ? null : policyInputs.getInputs()));
    try {
      tpsApiDispatch.createPao(new BearerToken(userRequest.getRequiredToken()), request);
    } catch (DuplicateObjectException e) {
      // TODO(zloery): catching unchecked exception seems bad
      // Before the flight we check that the workspace does not exist, so it's safe to assume that
      // any policy on this workspace object was created by this flight, and we can ignore conflicts
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
    tpsApiDispatch.deletePao(
        new BearerToken(userRequest.getRequiredToken()), workspace.getWorkspaceId());
    return StepResult.getStepResultSuccess();
  }
}
