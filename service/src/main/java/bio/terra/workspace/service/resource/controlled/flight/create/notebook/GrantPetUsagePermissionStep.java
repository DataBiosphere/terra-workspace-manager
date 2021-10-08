package bio.terra.workspace.service.resource.controlled.flight.create.notebook;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
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
 * Step to grant a user permission to use their pet service account. Controlled Notebooks run as a
 * pet service account, and only users with the 'iam.serviceAccounts.actAs' permission on the pet
 * are allowed through the proxy. GCP automatically de-deduplicates IAM bindings so this step is
 * idempotent, even across flights.
 */
public class GrantPetUsagePermissionStep implements Step {

  private static final Logger logger = LoggerFactory.getLogger(GrantPetUsagePermissionStep.class);

  private final UUID workspaceId;
  private final PetSaService petSaService;
  private final SamService samService;
  private final AuthenticatedUserRequest userRequest;

  public GrantPetUsagePermissionStep(
      UUID workspaceId,
      PetSaService petSaService,
      SamService samService,
      AuthenticatedUserRequest userRequest) {
    this.workspaceId = workspaceId;
    this.petSaService = petSaService;
    this.samService = samService;
    this.userRequest = userRequest;
  }

  @Override
  public StepResult doStep(FlightContext context) throws InterruptedException, RetryException {
    String userEmail = samService.getUserEmailFromSam(userRequest);
    Policy modifiedPolicy =
        petSaService.enablePetServiceAccountImpersonation(workspaceId, userEmail, userRequest);
    // Store the eTag value of the modified policy in case this step needs to be undone.
    FlightMap workingMap = context.getWorkingMap();
    workingMap.put(PetSaKeys.MODIFIED_PET_SA_POLICY_ETAG, modifiedPolicy.getEtag());
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    FlightMap workingMap = context.getWorkingMap();
    String userEmail = samService.getUserEmailFromSam(userRequest);
    String expectedEtag = workingMap.get(PetSaKeys.MODIFIED_PET_SA_POLICY_ETAG, String.class);
    if (expectedEtag == null) {
      // If the do step did not finish, we cannot guarantee we aren't undoing something we didn't do
      logger.info(
          "Unable to undo GrantUsagePermissionStep for user {} in workspace {} as do step did not complete.",
          userEmail,
          workspaceId);
      return StepResult.getStepResultSuccess();
    }
    petSaService.disablePetServiceAccountImpersonationWithEtag(
        workspaceId, userEmail, expectedEtag, userRequest);
    return StepResult.getStepResultSuccess();
  }
}
