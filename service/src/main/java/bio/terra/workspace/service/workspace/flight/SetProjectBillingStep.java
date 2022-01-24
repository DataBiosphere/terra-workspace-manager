package bio.terra.workspace.service.workspace.flight;

import bio.terra.cloudres.google.billing.CloudBillingClientCow;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import com.google.cloud.billing.v1.ProjectBillingInfo;

/** A {@link Step} to set the billing account on the Google project. */
public class SetProjectBillingStep implements Step {
  private final CloudBillingClientCow billingClient;

  public SetProjectBillingStep(CloudBillingClientCow billingClient) {
    this.billingClient = billingClient;
  }

  @Override
  public StepResult doStep(FlightContext flightContext) {
    String projectId =
        flightContext.getWorkingMap().get(WorkspaceFlightMapKeys.GCP_PROJECT_ID, String.class);
    String billingAccountId =
        flightContext.getWorkingMap().get(WorkspaceFlightMapKeys.BILLING_ACCOUNT_ID, String.class);
    ProjectBillingInfo setBilling =
        ProjectBillingInfo.newBuilder()
            .setBillingAccountName("billingAccounts/" + billingAccountId)
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
