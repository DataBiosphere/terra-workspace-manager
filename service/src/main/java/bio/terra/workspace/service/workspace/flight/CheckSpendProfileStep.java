package bio.terra.workspace.service.workspace.flight;

import static bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.BILLING_ACCOUNT_ID;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.db.WorkspaceDao;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.spendprofile.SpendProfile;
import bio.terra.workspace.service.spendprofile.SpendProfileId;
import bio.terra.workspace.service.spendprofile.SpendProfileService;
import bio.terra.workspace.service.workspace.exceptions.MissingSpendProfileException;
import bio.terra.workspace.service.workspace.exceptions.NoBillingAccountException;
import bio.terra.workspace.service.workspace.model.Workspace;
import java.util.UUID;

/** This is a step to check that the user is authorized to use the workspace spend profile. */
public class CheckSpendProfileStep implements Step {

  private final WorkspaceDao workspaceDao;
  private final SpendProfileService spendProfileService;
  private final UUID workspaceId;
  private final AuthenticatedUserRequest userRequest;

  public CheckSpendProfileStep(
      WorkspaceDao workspaceDao,
      SpendProfileService spendProfileService,
      UUID workspaceId,
      AuthenticatedUserRequest userRequest) {
    this.workspaceDao = workspaceDao;
    this.spendProfileService = spendProfileService;
    this.workspaceId = workspaceId;
    this.userRequest = userRequest;
  }

  @Override
  public StepResult doStep(FlightContext context) throws InterruptedException, RetryException {
    FlightMap workingMap = context.getWorkingMap();
    Workspace workspace = workspaceDao.getWorkspace(workspaceId);
    SpendProfileId spendProfileId =
        workspace
            .getSpendProfileId()
            .orElseThrow(
                () -> MissingSpendProfileException.forWorkspace(workspaceId));

    SpendProfile spendProfile = spendProfileService.authorizeLinking(spendProfileId, userRequest);
    if (spendProfile.billingAccountId().isEmpty()) {
      throw NoBillingAccountException.forSpendProfile(spendProfileId);
    }
    workingMap.put(BILLING_ACCOUNT_ID, spendProfile.billingAccountId());
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    return StepResult.getStepResultSuccess();
  }
}
