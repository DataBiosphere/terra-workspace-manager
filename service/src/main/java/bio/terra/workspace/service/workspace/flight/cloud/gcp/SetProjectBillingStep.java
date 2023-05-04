package bio.terra.workspace.service.workspace.flight.cloud.gcp;

import bio.terra.cloudres.google.billing.CloudBillingClientCow;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.workspace.common.utils.FlightUtils;
import bio.terra.workspace.service.spendprofile.SpendProfile;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import com.google.cloud.billing.v1.ProjectBillingInfo;

/** A {@link Step} to set the billing account on the Google project. */
public class SetProjectBillingStep implements Step {
  private final CloudBillingClientCow billingClient;

  public SetProjectBillingStep(CloudBillingClientCow billingClient) {
    this.billingClient = billingClient;
  }

  @Override
  public StepResult doStep(FlightContext flightContext) {
    FlightMap workingMap = flightContext.getWorkingMap();
    var spendProfile =
        FlightUtils.getRequired(
            workingMap, WorkspaceFlightMapKeys.SPEND_PROFILE, SpendProfile.class);
    var projectId =
        FlightUtils.getRequired(workingMap, WorkspaceFlightMapKeys.GCP_PROJECT_ID, String.class);

    ProjectBillingInfo setBilling =
        ProjectBillingInfo.newBuilder()
            .setBillingAccountName("billingAccounts/" + spendProfile.billingAccountId())
            .build();
    billingClient.updateProjectBillingInfo("projects/" + projectId, setBilling);
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) {
    // We're going to delete the project, so we don't need to worry about removing the billing
    // account.
    return StepResult.getStepResultSuccess();
  }
}
