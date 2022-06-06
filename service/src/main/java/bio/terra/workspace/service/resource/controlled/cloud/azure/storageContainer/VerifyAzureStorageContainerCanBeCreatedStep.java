package bio.terra.workspace.service.resource.controlled.cloud.azure.storageContainer;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.app.configuration.external.AzureConfiguration;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.resource.exception.DuplicateResourceException;
import bio.terra.workspace.service.resource.exception.ResourceNotFoundException;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import bio.terra.workspace.service.workspace.model.AzureCloudContext;
import com.azure.core.management.exception.ManagementException;
import com.azure.resourcemanager.storage.StorageManager;
import org.apache.commons.lang3.StringUtils;


/**
 * Verifies that the storage account exists in the workspace, and that the storage container does not already exist.
 * This step is designed to run immediately before {@link CreateAzureStorageContainerStep} to ensure idempotency
 * of the create operation.
 */
public class VerifyAzureStorageContainerCanBeCreatedStep implements Step {

  private final AzureConfiguration azureConfig;
  private final CrlService crlService;
  private final ControlledAzureStorageContainerResource resource;

  public VerifyAzureStorageContainerCanBeCreatedStep(
      AzureConfiguration azureConfig,
      CrlService crlService,
      ControlledAzureStorageContainerResource resource) {
    this.azureConfig = azureConfig;
    this.crlService = crlService;
    this.resource = resource;
  }

  @Override
  public StepResult doStep(FlightContext context) throws InterruptedException, RetryException {
    final AzureCloudContext azureCloudContext =
            context.getWorkingMap().get(ControlledResourceKeys.AZURE_CLOUD_CONTEXT, AzureCloudContext.class);
    final StorageManager storageManager = crlService.getStorageManager(azureCloudContext, azureConfig);

    // Check to see if the storage account is one of the controlled resources of this workspace.
    try {
      storageManager
              .storageAccounts()
              .getByResourceGroup(
                      azureCloudContext.getAzureResourceGroupId(), resource.getStorageAccountName());
    } catch (ManagementException ex) {
      return new StepResult(
              StepStatus.STEP_RESULT_FAILURE_FATAL, new ResourceNotFoundException(
              String.format("The Azure storage account with name '%s' cannot be retrieved.",
                      resource.getStorageAccountName())));
    }
    try {
      storageManager.blobContainers().get(
              azureCloudContext.getAzureResourceGroupId(),
              resource.getStorageAccountName(),
              resource.getStorageContainerName()
      );
      return new StepResult(
              StepStatus.STEP_RESULT_FAILURE_FATAL,
              new DuplicateResourceException(
                      String.format(
                              "An Azure Storage Container with name '%s' already exists in storage account '%s'",
                              resource.getStorageContainerName(), resource.getStorageAccountName())));
    } catch (ManagementException e) {
      // Azure error codes can be found here: // TODO: look here
      // https://docs.microsoft.com/en-us/azure/azure-resource-manager/templates/common-deployment-errors
      if (StringUtils.contains(e.getValue().getCode(), "ResourceNotFound")) {
        return StepResult.getStepResultSuccess();
      }
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY, e);
    }
  }


  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    // Nothing to undo
    return StepResult.getStepResultSuccess();
  }
}
