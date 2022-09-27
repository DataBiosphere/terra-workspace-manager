package bio.terra.workspace.service.workspace.flight;

import bio.terra.common.iam.BearerToken;
import bio.terra.policy.common.exception.PolicyObjectNotFoundException;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.amalgam.tps.TpsApiDispatch;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DeleteWorkspacePoliciesStep implements Step {

  private static final Logger logger = LoggerFactory.getLogger(DeleteWorkspacePoliciesStep.class);
  private final TpsApiDispatch tpsApiDispatch;
  private final AuthenticatedUserRequest userRequest;
  private final UUID workspaceId;

  public DeleteWorkspacePoliciesStep(
      TpsApiDispatch tpsApiDispatch, AuthenticatedUserRequest userRequest, UUID workspaceId) {
    this.tpsApiDispatch = tpsApiDispatch;
    this.userRequest = userRequest;
    this.workspaceId = workspaceId;
  }

  @Override
  public StepResult doStep(FlightContext context) throws InterruptedException, RetryException {
    try {
      tpsApiDispatch.deletePao(new BearerToken(userRequest.getRequiredToken()), workspaceId);
    } catch (PolicyObjectNotFoundException ex) {
      // PAO was not found, nothing to do here.
    }
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    // We can't un-delete the PAO, so just surface the error that caused the flight to fail.
    logger.error("Unable to undo deletion of policies on workspace {} in WSM DB", workspaceId);
    return context.getResult();
  }
}
