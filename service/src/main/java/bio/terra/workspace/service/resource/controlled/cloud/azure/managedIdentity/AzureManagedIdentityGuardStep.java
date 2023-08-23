package bio.terra.workspace.service.resource.controlled.cloud.azure.managedIdentity;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.app.configuration.external.AzureConfiguration;
import bio.terra.workspace.common.exception.AzureManagementExceptionUtils;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.resource.exception.DuplicateResourceException;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import bio.terra.workspace.service.workspace.model.AzureCloudContext;
import com.azure.core.management.exception.ManagementException;
import org.springframework.http.HttpStatus;

/**
 * Gets an Azure Managed Identity, and fails if it already exists. This step is designed to run
 * immediately before {@link CreateAzureManagedIdentityStep} to ensure idempotency of the create
 * operation.
 */
public class AzureManagedIdentityGuardStep implements Step {
  private final AzureConfiguration azureConfig;
  private final CrlService crlService;
  private final ControlledAzureManagedIdentityResource resource;

  public AzureManagedIdentityGuardStep(
      AzureConfiguration azureConfig,
      CrlService crlService,
      ControlledAzureManagedIdentityResource resource) {
    this.azureConfig = azureConfig;
    this.crlService = crlService;
    this.resource = resource;
  }

  @Override
  public StepResult doStep(FlightContext context) throws InterruptedException, RetryException {
    final AzureCloudContext azureCloudContext =
        context
            .getWorkingMap()
            .get(ControlledResourceKeys.AZURE_CLOUD_CONTEXT, AzureCloudContext.class);
    var msiManager = crlService.getMsiManager(azureCloudContext, azureConfig);
    try {
      msiManager
          .identities()
          .getByResourceGroup(
              azureCloudContext.getAzureResourceGroupId(), resource.getManagedIdentityName());
      return new StepResult(
          StepStatus.STEP_RESULT_FAILURE_FATAL,
          new DuplicateResourceException(
              String.format(
                  "An Azure Managed Identity with name %s already exists in resource group %s",
                  azureCloudContext.getAzureResourceGroupId(), resource.getManagedIdentityName())));
    } catch (ManagementException e) {
      if (e.getResponse().getStatusCode() == HttpStatus.NOT_FOUND.value()) {
        return StepResult.getStepResultSuccess();
      }
      return new StepResult(AzureManagementExceptionUtils.maybeRetryStatus(e), e);
    }
  }

  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    // Nothing to undo
    return StepResult.getStepResultSuccess();
  }
}
