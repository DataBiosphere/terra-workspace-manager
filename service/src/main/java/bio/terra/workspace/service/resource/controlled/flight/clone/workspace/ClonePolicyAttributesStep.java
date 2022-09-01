package bio.terra.workspace.service.resource.controlled.flight.clone.workspace;

import bio.terra.common.iam.BearerToken;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.amalgam.tps.TpsApiDispatch;
import bio.terra.workspace.common.utils.FlightUtils;
import bio.terra.workspace.generated.model.ApiTpsPaoGetResult;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.job.JobMapKeys;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import bio.terra.workspace.service.workspace.model.Workspace;
import java.util.Optional;
import java.util.UUID;

public class ClonePolicyAttributesStep implements Step {
  TpsApiDispatch tpsApiDispatch;

  public ClonePolicyAttributesStep(TpsApiDispatch tpsApiDispatch) {
    this.tpsApiDispatch = tpsApiDispatch;
  }

  @Override
  public StepResult doStep(FlightContext context) throws InterruptedException, RetryException {
    final FlightMap inputParameters = context.getInputParameters();
    FlightUtils.validateRequiredEntries(
        inputParameters,
        ControlledResourceKeys.SOURCE_WORKSPACE_ID,
        JobMapKeys.REQUEST.getKeyName(),
        JobMapKeys.AUTH_USER_INFO.getKeyName());

    final var sourceWorkspaceId =
        context.getInputParameters().get(ControlledResourceKeys.SOURCE_WORKSPACE_ID, UUID.class);
    final var destinationWorkspace =
        context.getInputParameters().get(JobMapKeys.REQUEST.getKeyName(), Workspace.class);
    final var userRequest =
        context
            .getInputParameters()
            .get(JobMapKeys.AUTH_USER_INFO.getKeyName(), AuthenticatedUserRequest.class);

    Optional<ApiTpsPaoGetResult> workspacePao =
        tpsApiDispatch.getPaoIfExists(
            new BearerToken(userRequest.getRequiredToken()), sourceWorkspaceId);

    if (workspacePao.isPresent()) {
      // If this is our first attempt, a default PAO would have been created with the workspace.
      // If this is a retry, then our previous attempt at saving a PAO might exist.
      // In either case, remove the existing PAO so we can clone from the source.
      tpsApiDispatch.deletePao(
          new BearerToken(userRequest.getRequiredToken()), destinationWorkspace.getWorkspaceId());
    }

    tpsApiDispatch.clonePao(
        new BearerToken(userRequest.getRequiredToken()),
        sourceWorkspaceId,
        destinationWorkspace.getWorkspaceId());

    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    final var destinationWorkspace =
        context.getInputParameters().get(JobMapKeys.REQUEST.getKeyName(), Workspace.class);
    final var userRequest =
        context
            .getInputParameters()
            .get(JobMapKeys.AUTH_USER_INFO.getKeyName(), AuthenticatedUserRequest.class);

    // Delete PAO doesn't throw on repeated invocations or if the object is missing.
    tpsApiDispatch.deletePao(
        new BearerToken(userRequest.getRequiredToken()), destinationWorkspace.getWorkspaceId());
    return StepResult.getStepResultSuccess();
  }
}
