package bio.terra.workspace.service.resource.controlled.flight.clone;

import bio.terra.common.iam.BearerToken;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.amalgam.tps.TpsApiDispatch;
import bio.terra.workspace.generated.model.ApiTpsComponent;
import bio.terra.workspace.generated.model.ApiTpsObjectType;
import bio.terra.workspace.generated.model.ApiTpsPaoCreateRequest;
import bio.terra.workspace.generated.model.ApiTpsPaoGetResult;
import bio.terra.workspace.generated.model.ApiTpsPaoSourceRequest;
import bio.terra.workspace.generated.model.ApiTpsPaoUpdateResult;
import bio.terra.workspace.generated.model.ApiTpsPolicyInputs;
import bio.terra.workspace.generated.model.ApiTpsUpdateMode;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.resource.exception.PolicyConflictException;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ClonePolicyAttributesStep implements Step {
  private static final Logger logger = LoggerFactory.getLogger(ClonePolicyAttributesStep.class);

  private final UUID sourceWorkspaceId;
  private final UUID destinationWorkspaceId;
  private final AuthenticatedUserRequest userRequest;
  private final TpsApiDispatch tpsApiDispatch;

  public ClonePolicyAttributesStep(
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
    BearerToken token = new BearerToken(userRequest.getRequiredToken());

    // Create PAOs if they don't exist; catch TPS exceptions and retry
    try {
      createPaoIfNotExist(token, sourceWorkspaceId);
      createPaoIfNotExist(token, destinationWorkspaceId);
    } catch (Exception ex) {
      logger.info("Attempt to create a PAO for workspace failed", ex);
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY, ex);
    }

    // Save the destination attributes so we can restore them if the flight fails
    ApiTpsPaoGetResult destinationPao = tpsApiDispatch.getPao(token, destinationWorkspaceId);
    ApiTpsPolicyInputs destinationAttributes = destinationPao.getAttributes();
    context.getWorkingMap().put(WorkspaceFlightMapKeys.POLICIES, destinationAttributes);

    ApiTpsPaoSourceRequest request =
        new ApiTpsPaoSourceRequest()
            .sourceObjectId(sourceWorkspaceId)
            .updateMode(ApiTpsUpdateMode.FAIL_ON_CONFLICT);

    ApiTpsPaoUpdateResult result = tpsApiDispatch.mergePao(token, destinationWorkspaceId, request);
    if (!result.isSucceeded()) {
      List<String> conflictList =
          result.getConflicts().stream().map(c -> c.getNamespace() + ':' + c.getName()).toList();
      throw new PolicyConflictException(
          "Destination workspace policies conflict with source workspace policies", conflictList);
    }

    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    // var destinationAttributes =
    //  context.getWorkingMap().get(WorkspaceFlightMapKeys.POLICIES, ApiTpsPolicyInputs.class);
    // TODO: PF-2019 - when we have a replace method, replace the destination PAO attributes
    return StepResult.getStepResultSuccess();
  }

  // Since policy attributes were added later in development, not all existing
  // workspaces have an associated policy attribute object. This method creates
  // an empty one if it does not exist.
  private void createPaoIfNotExist(BearerToken token, UUID workspaceId) {
    Optional<ApiTpsPaoGetResult> pao = tpsApiDispatch.getPaoIfExists(token, workspaceId);
    if (pao.isPresent()) {
      return;
    }
    // Workspace doesn't have a PAO, so create an empty one for it.
    ApiTpsPaoCreateRequest request =
        new ApiTpsPaoCreateRequest()
            .objectId(workspaceId)
            .component(ApiTpsComponent.WSM)
            .objectType(ApiTpsObjectType.WORKSPACE)
            .attributes(new ApiTpsPolicyInputs());
    tpsApiDispatch.createPao(token, request);
  }
}
