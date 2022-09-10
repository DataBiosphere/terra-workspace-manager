package bio.terra.workspace.service.resource.controlled.flight.clone.workspace;

import bio.terra.common.iam.BearerToken;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.amalgam.tps.TpsApiDispatch;
import bio.terra.workspace.common.utils.FlightUtils;
import bio.terra.workspace.generated.model.ApiTpsComponent;
import bio.terra.workspace.generated.model.ApiTpsObjectType;
import bio.terra.workspace.generated.model.ApiTpsPaoCreateRequest;
import bio.terra.workspace.generated.model.ApiTpsPaoGetResult;
import bio.terra.workspace.generated.model.ApiTpsPolicyInputs;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.job.JobMapKeys;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import bio.terra.workspace.service.workspace.model.Workspace;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ClonePolicyAttributesStep implements Step {
  private static final Logger logger = LoggerFactory.getLogger(ClonePolicyAttributesStep.class);
  TpsApiDispatch tpsApiDispatch;

  public ClonePolicyAttributesStep(TpsApiDispatch tpsApiDispatch) {
    this.tpsApiDispatch = tpsApiDispatch;
  }

  @Override
  public StepResult doStep(FlightContext context) throws InterruptedException, RetryException {
    FlightMap inputParameters = context.getInputParameters();
    FlightUtils.validateRequiredEntries(
        inputParameters,
        ControlledResourceKeys.SOURCE_WORKSPACE_ID,
        JobMapKeys.REQUEST.getKeyName(),
        JobMapKeys.AUTH_USER_INFO.getKeyName());

    var sourceWorkspaceId =
        context.getInputParameters().get(ControlledResourceKeys.SOURCE_WORKSPACE_ID, UUID.class);
    var destinationWorkspace =
        context.getInputParameters().get(JobMapKeys.REQUEST.getKeyName(), Workspace.class);
    var userRequest =
        context
            .getInputParameters()
            .get(JobMapKeys.AUTH_USER_INFO.getKeyName(), AuthenticatedUserRequest.class);

    Optional<ApiTpsPaoGetResult> sourcePao =
        tpsApiDispatch.getPaoIfExists(
            new BearerToken(userRequest.getRequiredToken()), sourceWorkspaceId);

    if (!sourcePao.isPresent()) {
      // Source workspace doesn't have a PAO, so create one for it.
      ApiTpsPaoCreateRequest request =
          new ApiTpsPaoCreateRequest()
              .objectId(sourceWorkspaceId)
              .component(ApiTpsComponent.WSM)
              .objectType(ApiTpsObjectType.WORKSPACE)
              .attributes(new ApiTpsPolicyInputs());
      try {
        tpsApiDispatch.createPao(new BearerToken(userRequest.getRequiredToken()), request);
      } catch (Exception ex) {
        logger.info("Attempt to create a default PAO for source failed: " + sourceWorkspaceId, ex);
        return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY, ex);
      }
    }

    Optional<ApiTpsPaoGetResult> destinationPao =
        tpsApiDispatch.getPaoIfExists(
            new BearerToken(userRequest.getRequiredToken()), destinationWorkspace.getWorkspaceId());

    if (destinationPao.isPresent()) {
      // If this is our first attempt, a default PAO would have been created with the workspace.
      // If this is a retry, then our previous attempt at saving a PAO might exist.
      // In either case, remove the existing PAO so we can clone from the source.
      try {
        tpsApiDispatch.deletePao(
            new BearerToken(userRequest.getRequiredToken()), destinationWorkspace.getWorkspaceId());
      } catch (Exception ex) {
        logger.info(
            "Attempt to remove PAO for cloned workspace failed: "
                + destinationWorkspace.getWorkspaceId(),
            ex);
        return new StepResult(StepStatus.STEP_RESULT_FAILURE_FATAL, ex);
      }
    }

    tpsApiDispatch.clonePao(
        new BearerToken(userRequest.getRequiredToken()),
        sourceWorkspaceId,
        destinationWorkspace.getWorkspaceId());

    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    var destinationWorkspace =
        context.getInputParameters().get(JobMapKeys.REQUEST.getKeyName(), Workspace.class);
    var userRequest =
        context
            .getInputParameters()
            .get(JobMapKeys.AUTH_USER_INFO.getKeyName(), AuthenticatedUserRequest.class);

    // Delete PAO doesn't throw on repeated invocations or if the object is missing.
    tpsApiDispatch.deletePao(
        new BearerToken(userRequest.getRequiredToken()), destinationWorkspace.getWorkspaceId());
    return StepResult.getStepResultSuccess();
  }
}
