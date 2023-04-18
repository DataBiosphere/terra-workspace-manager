package bio.terra.workspace.service.workspace.flight.cloud.gcp;

import static bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.GCP_PROJECT_ID;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;

/**
 * Step to create the caller's pet service account in a newly-created GCP context. There can be a
 * brief delay between SA creation and propagation of proxy-group permissions within GCP, so we
 * pre-emptively create the caller's pet SA during GCP context creation instead of creating it the
 * first time it's needed.
 */
public class CreatePetSaStep implements Step {

  private final SamService samService;
  private final AuthenticatedUserRequest userRequest;

  public CreatePetSaStep(SamService samService, AuthenticatedUserRequest userRequest) {
    this.samService = samService;
    this.userRequest = userRequest;
  }

  @Override
  public StepResult doStep(FlightContext context) throws InterruptedException, RetryException {
    String projectId = context.getWorkingMap().get(GCP_PROJECT_ID, String.class);
    final AuthenticatedUserRequest petSaCredentials =
        samService.getOrCreatePetSaCredentials(projectId, userRequest);
    context.getWorkingMap().put(WorkspaceFlightMapKeys.PET_SA_CREDENTIALS, petSaCredentials);
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    // Sam does not support deleting pet SAs, so nothing to undo here.
    return StepResult.getStepResultSuccess();
  }
}
