package bio.terra.workspace.service.resource.controlled.cloud.azure.managedIdentity;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.app.configuration.external.AzureConfiguration;
import bio.terra.workspace.common.exception.AzureManagementExceptionUtils;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.resource.controlled.flight.delete.DeleteControlledResourceStep;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import bio.terra.workspace.service.workspace.model.AzureCloudContext;
import com.azure.core.management.exception.ManagementException;

/**
 * Gets an Azure Managed Identity for a user's pet.
 *
 *  This implements the marker interface DeleteControlledResourceStep,
 *  in order to indicate that it is also used when deleting the resource.
 * */
public class GetPetManagedIdentityStep implements DeleteControlledResourceStep, GetManagedIdentityStep {
  private final AzureConfiguration azureConfig;
  private final CrlService crlService;
  private final SamService samService;
  private final String userEmail;

  public GetPetManagedIdentityStep(
      AzureConfiguration azureConfig,
      CrlService crlService,
      SamService samService,
      String userEmail) {
    this.azureConfig = azureConfig;
    this.crlService = crlService;
    this.samService = samService;
    this.userEmail = userEmail;
  }

  @Override
  public StepResult doStep(FlightContext context) throws InterruptedException, RetryException {
    final AzureCloudContext azureCloudContext =
        context
            .getWorkingMap()
            .get(ControlledResourceKeys.AZURE_CLOUD_CONTEXT, AzureCloudContext.class);
    var msiManager = crlService.getMsiManager(azureCloudContext, azureConfig);

    var objectId =
        samService.getOrCreateUserManagedIdentityForUser(
            userEmail,
            azureCloudContext.getAzureSubscriptionId(),
            azureCloudContext.getAzureTenantId(),
            azureCloudContext.getAzureResourceGroupId());

    try {
      var uami = msiManager.identities().getById(objectId);

      putManagedIdentityInContext(context, uami);

      return StepResult.getStepResultSuccess();
    } catch (ManagementException e) {
      return new StepResult(AzureManagementExceptionUtils.maybeRetryStatus(e), e);
    }
  }

  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    // Nothing to undo
    return StepResult.getStepResultSuccess();
  }
}
