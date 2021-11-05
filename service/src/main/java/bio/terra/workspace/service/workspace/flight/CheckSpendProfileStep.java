package bio.terra.workspace.service.workspace.flight;

import static bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.BILLING_ACCOUNT_ID;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.spendprofile.SpendProfile;
import bio.terra.workspace.service.spendprofile.SpendProfileId;
import bio.terra.workspace.service.spendprofile.SpendProfileService;
import bio.terra.workspace.service.workspace.exceptions.NoBillingAccountException;
import java.util.Optional;

/**
 * This is a step to check for an authorized billing account for a given spend profile.
 */
public class CheckSpendProfileStep implements Step {

  private final SpendProfileService spendProfileService;
  private final AuthenticatedUserRequest userRequest;

  public CheckSpendProfileStep(SpendProfileService spendProfileService, AuthenticatedUserRequest userRequest) {
    this.spendProfileService = spendProfileService;
    this.userRequest = userRequest;
  }

  @Override
  public StepResult doStep(FlightContext context) throws InterruptedException, RetryException {
    FlightMap workingMap = context.getWorkingMap();
    String spendProfileIdString = context.getInputParameters().get(WorkspaceFlightMapKeys.SPEND_PROFILE_ID, String.class);
    SpendProfileId spendProfileId =
        Optional.ofNullable(spendProfileIdString).map(SpendProfileId::create).orElse(null);

    SpendProfile spendProfile = spendProfileService.authorizeLinking(spendProfileId, userRequest);
    if (spendProfile.billingAccountId().isEmpty()) {
      throw new NoBillingAccountException(spendProfileId);
    }
   workingMap.put(BILLING_ACCOUNT_ID, spendProfile.billingAccountId());
   return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    return StepResult.getStepResultSuccess();
  }
}
