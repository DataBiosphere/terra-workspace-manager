package bio.terra.workspace.service.workspace.flight;

import bio.terra.common.iam.BearerToken;
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

public class CreateWorkspacePoliciesStep implements Step {

  private final Workspace workspace;
  private final ApiTpsPolicyInputs policyInputs;
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
            .attributes(new ApiTpsPolicyInputs().inputs(policyInputs.getInputs()));
    tpsApiDispatch.createPao(new BearerToken(userRequest.getRequiredToken()), request);
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    // Before the flight we check that the workspace does not exist before this flight runs, so it's
    // safe to assume that any policy on this workspace object was created by this flight.
    tpsApiDispatch.deletePao(
        new BearerToken(userRequest.getRequiredToken()), workspace.getWorkspaceId());
    return StepResult.getStepResultSuccess();
  }
}
