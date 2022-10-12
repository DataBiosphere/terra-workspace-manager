package bio.terra.workspace.service.workspace.flight;

import static bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.AZURE_BILLING_MANAGED_RESOURCE_GROUP_ID;
import static bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.AZURE_BILLING_SUBSCRIPTION_ID;
import static bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.AZURE_BILLING_TENANT_ID;
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
import bio.terra.workspace.service.workspace.exceptions.NoAzureAppCoordinatesException;
import bio.terra.workspace.service.workspace.exceptions.NoBillingAccountException;
import bio.terra.workspace.service.workspace.model.CloudPlatform;
import bio.terra.workspace.service.workspace.model.Workspace;
import java.util.UUID;

/** This is a step to check that the user is authorized to use the workspace spend profile. */
public class CheckSpendProfileStep implements Step {

  private final WorkspaceDao workspaceDao;
  private final SpendProfileService spendProfileService;
  private final UUID workspaceUuid;
  private final AuthenticatedUserRequest userRequest;
  private final CloudPlatform cloudPlatform;
  private final boolean bpmEnabled;

  public CheckSpendProfileStep(
      WorkspaceDao workspaceDao,
      SpendProfileService spendProfileService,
      UUID workspaceUuid,
      AuthenticatedUserRequest userRequest,
      CloudPlatform cloudPlatform,
      boolean bpmEnabled) {
    this.workspaceDao = workspaceDao;
    this.spendProfileService = spendProfileService;
    this.workspaceUuid = workspaceUuid;
    this.userRequest = userRequest;
    this.cloudPlatform = cloudPlatform;
    this.bpmEnabled = bpmEnabled;
  }

  @Override
  public StepResult doStep(FlightContext context) throws InterruptedException, RetryException {
    FlightMap workingMap = context.getWorkingMap();
    Workspace workspace = workspaceDao.getWorkspace(workspaceUuid);
    SpendProfileId spendProfileId =
        workspace
            .getSpendProfileId()
            .orElseThrow(() -> MissingSpendProfileException.forWorkspace(workspaceUuid));

    SpendProfile spendProfile =
        spendProfileService.authorizeLinking(spendProfileId, bpmEnabled, userRequest);

    if (cloudPlatform == CloudPlatform.GCP) {
      if (spendProfile.billingAccountId().isEmpty()) {
        throw NoBillingAccountException.forSpendProfile(spendProfileId);
      }
      workingMap.put(BILLING_ACCOUNT_ID, spendProfile.billingAccountId());
    } else if (cloudPlatform == CloudPlatform.AZURE) {
      if (spendProfile.managedResourceGroupId().isEmpty()
          || spendProfile.subscriptionId().isEmpty()
          || spendProfile.tenantId().isEmpty()) {
        throw NoAzureAppCoordinatesException.forSpendProfile(spendProfileId);
      }
      workingMap.put(AZURE_BILLING_SUBSCRIPTION_ID, spendProfile.subscriptionId().get());
      workingMap.put(AZURE_BILLING_TENANT_ID, spendProfile.tenantId().get());
      workingMap.put(
          AZURE_BILLING_MANAGED_RESOURCE_GROUP_ID, spendProfile.managedResourceGroupId().get());
    }
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    return StepResult.getStepResultSuccess();
  }
}
