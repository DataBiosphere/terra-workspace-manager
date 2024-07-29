package bio.terra.workspace.service.workspace.flight.cloud.gcp;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.workspace.common.utils.FlightUtils;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.spendprofile.model.SpendProfile;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** A {@link Step} to set the billing account on the Google project. */
public class SetProjectBillingStep implements Step {
  private final CrlService crlService;
  private final SpendProfile spendProfile;
  private final Logger logger = LoggerFactory.getLogger(SetProjectBillingStep.class);

  public SetProjectBillingStep(CrlService crlService, SpendProfile spendProfile) {
    this.crlService = crlService;
    this.spendProfile = spendProfile;
  }

  @Override
  public StepResult doStep(FlightContext flightContext) {
    FlightMap workingMap = flightContext.getWorkingMap();
    var projectId =
        FlightUtils.getRequired(workingMap, WorkspaceFlightMapKeys.GCP_PROJECT_ID, String.class);

    logger.info(
        "Setting project billing on project {} to billing account {}",
        projectId,
        spendProfile.billingAccountId());
    crlService.updateGcpProjectBilling(projectId, spendProfile.billingAccountId());
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) {
    // We're going to delete the project, so we don't need to worry about removing the billing
    // account.
    return StepResult.getStepResultSuccess();
  }
}
