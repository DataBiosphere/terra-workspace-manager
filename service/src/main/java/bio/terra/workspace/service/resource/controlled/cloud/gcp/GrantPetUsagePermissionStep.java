package bio.terra.workspace.service.resource.controlled.cloud.gcp;

import bio.terra.common.exception.ConflictException;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.petserviceaccount.PetSaService;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.PetSaKeys;
import com.google.api.services.iam.v1.model.Policy;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Step to grant a user permission to use their pet service account. Compute instances run as a pet
 * service account, and only users with the 'iam.serviceAccounts.actAs' permission on the pet are
 * allowed through the proxy. GCP automatically de-deduplicates IAM bindings so this step is
 * idempotent, even across flights.
 *
 * <p>There is a small window where this step may fail to undo: specifically if the do step modifies
 * the pet SA's IAM policy but fails before storing the updated eTag in the working map. This is
 * indistinguishable from a simultaneous modification by another flight, so in that case we log a
 * warning and do not undo the flight to prevent potentially clobbering another flight's changes.
 */
public class GrantPetUsagePermissionStep implements Step {

  private static final Logger logger = LoggerFactory.getLogger(GrantPetUsagePermissionStep.class);

  private final UUID workspaceUuid;
  private final AuthenticatedUserRequest userRequest;
  private final PetSaService petSaService;
  private final SamService samService;

  public GrantPetUsagePermissionStep(
      UUID workspaceUuid,
      AuthenticatedUserRequest userRequest,
      PetSaService petSaService,
      SamService samService) {
    this.workspaceUuid = workspaceUuid;
    this.userRequest = userRequest;
    this.petSaService = petSaService;
    this.samService = samService;
  }

  @Override
  public StepResult doStep(FlightContext context) throws InterruptedException, RetryException {
    Policy modifiedPolicy;
    try {
      modifiedPolicy =
          petSaService.enablePetServiceAccountImpersonation(
              workspaceUuid,
              samService.getUserEmailFromSamAndRethrowOnInterrupt(userRequest),
              userRequest);
    } catch (ConflictException e) {
      // There was a conflict enabling the service account. Request retry.
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY, e);
    }

    // Store the eTag value of the modified policy in case this step needs to be undone.
    FlightMap workingMap = context.getWorkingMap();
    workingMap.put(PetSaKeys.MODIFIED_PET_SA_POLICY_ETAG, modifiedPolicy.getEtag());
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    FlightMap workingMap = context.getWorkingMap();
    String expectedEtag = workingMap.get(PetSaKeys.MODIFIED_PET_SA_POLICY_ETAG, String.class);
    if (expectedEtag == null) {
      // If the do step did not finish, we cannot guarantee we aren't undoing something we didn't do
      logger.warn(
          "Unable to undo GrantUsagePermissionStep for user {} in workspace {} as do step may not have completed.",
          userRequest.getEmail(),
          workspaceUuid);
      return StepResult.getStepResultSuccess();
    }
    try {
      // userRequest.email might be a pet SA (e.g. if the user is making this call from inside a
      // notebook), so we need to call Sam and determine the end-user's email directly.
      // TODO(PF-1001): Having a SamUser object here (and in doStep) instead of an
      //  AuthenticatedUserRequest would save an extra call to Sam.
      petSaService.disablePetServiceAccountImpersonationWithEtag(
          workspaceUuid,
          samService.getUserEmailFromSamAndRethrowOnInterrupt(userRequest),
          userRequest,
          expectedEtag);
    } catch (ConflictException e) {
      // There was a conflict disabling the service account. Request retry.
      // There is a possible concurrency error here: if two threads are trying to enable and
      // one succeeds and one fails, the failed one will disable the impersonation on undo.
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY, e);
    }

    return StepResult.getStepResultSuccess();
  }
}
