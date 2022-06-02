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
import com.azure.core.http.rest.PagedIterable;
import com.azure.core.management.exception.ManagementException;
import com.azure.resourcemanager.storage.StorageManager;
import com.azure.resourcemanager.storage.fluent.models.ListContainerItemInner;


/**
 * Gets an Azure Storage Container, and fails if it already exists or if the parent storage account does not exist.
 * This step is designed to run immediately before {@link CreateAzureStorageContainerStep} to ensure idempotency
 * of the create operation.
 */
public class GetAzureStorageContainerStep implements Step {

  private final AzureConfiguration azureConfig;
  private final CrlService crlService;
  private final ControlledAzureStorageContainerResource resource;

  public GetAzureStorageContainerStep(
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

    try {
      storageManager
              .storageAccounts()
              .getByResourceGroup(
                      azureCloudContext.getAzureResourceGroupId(), resource.getStorageAccountName());
    } catch (ManagementException ex) {
      return new StepResult(
              StepStatus.STEP_RESULT_FAILURE_FATAL, new ResourceNotFoundException(
              String.format("No Azure storage account with name '%s' exists for this workspace",
                      resource.getStorageAccountName())));
    }
    final PagedIterable<ListContainerItemInner> existingContainers = storageManager.blobContainers().list(
            azureCloudContext.getAzureResourceGroupId(), resource.getStorageAccountName()
    );
    for (ListContainerItemInner item : existingContainers) {
      if (item.name().equals(resource.getStorageContainerName())) {
        return new StepResult(
                StepStatus.STEP_RESULT_FAILURE_FATAL,
                new DuplicateResourceException(
                        String.format(
                                "An Azure Storage Container with name '%s' already exists in storage account '%s'",
                                resource.getStorageContainerName(), resource.getStorageAccountName())));

      }
    }
    return StepResult.getStepResultSuccess();
  }


  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    // Nothing to undo
    return StepResult.getStepResultSuccess();
  }
}
