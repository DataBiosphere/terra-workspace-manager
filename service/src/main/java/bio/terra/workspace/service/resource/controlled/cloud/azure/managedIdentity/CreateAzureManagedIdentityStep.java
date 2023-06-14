package bio.terra.workspace.service.resource.controlled.cloud.azure.managedIdentity;

import bio.terra.cloudres.azure.resourcemanager.common.Defaults;
import bio.terra.cloudres.azure.resourcemanager.msi.data.CreateUserAssignedManagedIdentityRequestData;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.app.configuration.external.AzureConfiguration;
import bio.terra.workspace.common.exception.AzureManagementExceptionUtils;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import bio.terra.workspace.service.workspace.model.AzureCloudContext;
import com.azure.core.management.Region;
import com.azure.core.management.exception.ManagementException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;

/**
 * Creates an Azure Managed Identity. Designed to run directly after {@link
 * GetAzureManagedIdentityStep}.
 */
public class CreateAzureManagedIdentityStep implements Step {

  private static final Logger logger =
      LoggerFactory.getLogger(CreateAzureManagedIdentityStep.class);
  private final AzureConfiguration azureConfig;
  private final CrlService crlService;
  private final ControlledAzureManagedIdentityResource resource;

  public CreateAzureManagedIdentityStep(
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
          .define(resource.getManagedIdentityName())
          .withRegion(resource.getRegion())
          .withExistingResourceGroup(azureCloudContext.getAzureResourceGroupId())
          .withTag("workspaceId", resource.getWorkspaceId().toString())
          .withTag("resourceId", resource.getResourceId().toString())
          .create(
              Defaults.buildContext(
                  CreateUserAssignedManagedIdentityRequestData.builder()
                      .setName(resource.getManagedIdentityName())
                      .setRegion(Region.fromName(resource.getRegion()))
                      .setResourceGroupName(azureCloudContext.getAzureResourceGroupId())
                      .setTenantId(azureCloudContext.getAzureTenantId())
                      .setSubscriptionId(azureCloudContext.getAzureSubscriptionId())
                      .build()));
    } catch (ManagementException e) {
      // Stairway steps may run multiple times, so we may already have created this resource
      if (e.getResponse().getStatusCode() == HttpStatus.CONFLICT.value()) {
        logger.info(
            "Azure Managed Identity {} in managed resource group {} already exists",
            resource.getManagedIdentityName(),
            azureCloudContext.getAzureResourceGroupId());
        return StepResult.getStepResultSuccess();
      }
      return new StepResult(AzureManagementExceptionUtils.maybeRetryStatus(e), e);
    }

    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    final AzureCloudContext azureCloudContext =
        context
            .getWorkingMap()
            .get(ControlledResourceKeys.AZURE_CLOUD_CONTEXT, AzureCloudContext.class);
    var msiManager = crlService.getMsiManager(azureCloudContext, azureConfig);

    try {
      msiManager
          .identities()
          .deleteByResourceGroup(
              azureCloudContext.getAzureResourceGroupId(), resource.getManagedIdentityName());
    } catch (ManagementException e) {
      // Stairway steps may run multiple times, so we may already have deleted this resource.
      if (e.getResponse().getStatusCode() == HttpStatus.NOT_FOUND.value()) {
        logger.info(
            "Azure Managed Identity {} in managed resource group {} already deleted",
            resource.getManagedIdentityName(),
            azureCloudContext.getAzureResourceGroupId());
        return StepResult.getStepResultSuccess();
      }
      return new StepResult(AzureManagementExceptionUtils.maybeRetryStatus(e), e);
    }
    return StepResult.getStepResultSuccess();
  }
}
