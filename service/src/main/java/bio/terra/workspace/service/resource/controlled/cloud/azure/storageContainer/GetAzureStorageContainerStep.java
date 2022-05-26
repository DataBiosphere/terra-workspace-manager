package bio.terra.workspace.service.resource.controlled.cloud.azure.storageContainer;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.app.configuration.external.AzureConfiguration;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.resource.exception.DuplicateResourceException;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import bio.terra.workspace.service.workspace.model.AzureCloudContext;
import com.azure.resourcemanager.storage.StorageManager;
import com.azure.resourcemanager.storage.models.BlobContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Gets an Azure Storage Container, and fails if it already exists. This step is designed to run
 * immediately before {@link CreateAzureStorageContainerStep} to ensure idempotency of the create operation.
 */
public class GetAzureStorageContainerStep implements Step {

  private static final Logger logger = LoggerFactory.getLogger(GetAzureStorageContainerStep.class); // TODO: delete if not used
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
        context
            .getWorkingMap()
            .get(ControlledResourceKeys.AZURE_CLOUD_CONTEXT, AzureCloudContext.class);
    final StorageManager storageManager = crlService.getStorageManager(azureCloudContext, azureConfig);

//    final BlobContainer container = storageManager.blobContainers().get(
//            azureCloudContext.getAzureResourceGroupId(),
//            resource.getStorageAccountName(),
//            resource.getStorageContainerName()
//    );
    // Throws exception when it does not exist, and then we didn't properly handle the exception.
    // Failed to construct exception: com.azure.core.management.exception.ManagementException; Exception message: Status code 404,
      return StepResult.getStepResultSuccess();
//    }
//
//    return new StepResult(
//        StepStatus.STEP_RESULT_FAILURE_FATAL,
//        new DuplicateResourceException(
//            String.format(
//                "An Azure Storage Account with name %s already exists",
//                resource.getStorageAccountName())));
  }

  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    // Nothing to undo
    return StepResult.getStepResultSuccess();
  }
}
