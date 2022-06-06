package bio.terra.workspace.service.resource.controlled.cloud.azure.storageContainer;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.app.configuration.external.AzureConfiguration;
import bio.terra.workspace.common.utils.ManagementExceptionUtils;
import bio.terra.workspace.db.ResourceDao;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.resource.controlled.cloud.azure.storage.ControlledAzureStorageResource;
import bio.terra.workspace.service.resource.exception.DuplicateResourceException;
import bio.terra.workspace.service.resource.exception.ResourceNotFoundException;
import bio.terra.workspace.service.resource.model.WsmResource;
import bio.terra.workspace.service.resource.model.WsmResourceType;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import bio.terra.workspace.service.workspace.model.AzureCloudContext;
import com.azure.core.management.exception.ManagementException;
import com.azure.resourcemanager.storage.StorageManager;


/**
 * Verifies that the storage account exists in the workspace, and that the storage container does not already exist.
 * This step is designed to run immediately before {@link CreateAzureStorageContainerStep} to ensure idempotency
 * of the create operation.
 */
public class VerifyAzureStorageContainerCanBeCreatedStep implements Step {

  private final AzureConfiguration azureConfig;
  private final CrlService crlService;
  private final ResourceDao resourceDao;
  private final ControlledAzureStorageContainerResource resource;

  public VerifyAzureStorageContainerCanBeCreatedStep(
      AzureConfiguration azureConfig,
      CrlService crlService,
      ResourceDao resourceDao,
      ControlledAzureStorageContainerResource resource) {
    this.azureConfig = azureConfig;
    this.crlService = crlService;
    this.resourceDao = resourceDao;
    this.resource = resource;
  }

  @Override
  public StepResult doStep(FlightContext context) throws InterruptedException, RetryException {
    final AzureCloudContext azureCloudContext =
            context.getWorkingMap().get(ControlledResourceKeys.AZURE_CLOUD_CONTEXT, AzureCloudContext.class);
    final StorageManager storageManager = crlService.getStorageManager(azureCloudContext, azureConfig);

    try {
      final WsmResource wsmResource = resourceDao.getResource(resource.getWorkspaceId(), resource.getStorageAccountId());
      final ControlledAzureStorageResource storageAccount = wsmResource.castToControlledResource().castByEnum(
              WsmResourceType.CONTROLLED_AZURE_STORAGE_ACCOUNT);

      context.getWorkingMap().put(ControlledResourceKeys.STORAGE_ACCOUNT_NAME, storageAccount.getStorageAccountName());

      storageManager.storageAccounts().getByResourceGroup(
              azureCloudContext.getAzureResourceGroupId(), storageAccount.getStorageAccountName());
    }
    catch (ResourceNotFoundException resourceNotFoundException) { // Thrown by resourceDao.getResource
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_FATAL, resourceNotFoundException);
    } catch (ManagementException managementException) { // Thrown by storageManager
      if (ManagementExceptionUtils.isResourceNotFound(managementException)) {
        return new StepResult(
                StepStatus.STEP_RESULT_FAILURE_FATAL, new ResourceNotFoundException(
                String.format("The storage account with ID '%s' cannot be retrieved from Azure.",
                        resource.getStorageAccountId())));
      }
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY, managementException);
    }

    try {
      final String storageAccountName = context.getWorkingMap().get(ControlledResourceKeys.STORAGE_ACCOUNT_NAME, String.class);
      storageManager.blobContainers().get(
              azureCloudContext.getAzureResourceGroupId(),
              storageAccountName,
              resource.getStorageContainerName()
      );
      return new StepResult(
              StepStatus.STEP_RESULT_FAILURE_FATAL,
              new DuplicateResourceException(
                      String.format(
                              "An Azure Storage Container with name '%s' already exists in storage account '%s'",
                              resource.getStorageContainerName(), storageAccountName)));
    } catch (ManagementException e) {
      if (ManagementExceptionUtils.isContainerNotFound(e)) {
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
