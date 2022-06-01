package bio.terra.workspace.service.resource.controlled.cloud.azure.storage;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.workspace.app.configuration.external.AzureConfiguration;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import bio.terra.workspace.service.workspace.model.AzureCloudContext;
import com.azure.resourcemanager.storage.StorageManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** A step for deleting a controlled Azure Storage Account resource. */
public class DeleteAzureStorageStep implements Step {
  private static final Logger logger = LoggerFactory.getLogger(DeleteAzureStorageStep.class);
  private final AzureConfiguration azureConfig;
  private final CrlService crlService;
  private final ControlledAzureStorageResource resource;

  public DeleteAzureStorageStep(
      AzureConfiguration azureConfig,
      CrlService crlService,
      ControlledAzureStorageResource resource) {
    this.crlService = crlService;
    this.azureConfig = azureConfig;
    this.resource = resource;
  }

  @Override
  public StepResult doStep(FlightContext context) throws InterruptedException {
    final AzureCloudContext azureCloudContext =
        context
            .getWorkingMap()
            .get(ControlledResourceKeys.AZURE_CLOUD_CONTEXT, AzureCloudContext.class);

    final StorageManager manager = crlService.getStorageManager(azureCloudContext, azureConfig);
    try {
      logger.info("Attempting to delete storage account: {}", resource.getStorageAccountName());
      manager
          .storageAccounts()
          .deleteByResourceGroup(
              azureCloudContext.getAzureResourceGroupId(), resource.getStorageAccountName());
      return StepResult.getStepResultSuccess();
    } catch (Exception ex) {
      logger.info(
          "Attempt to delete Azure Storage Account failed on this try: "
              + resource.getStorageAccountName(),
          ex);
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY, ex);
    }
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) throws InterruptedException {
    logger.error(
        "Cannot undo delete of Azure Storage Account resource {} in workspace {}.",
        resource.getResourceId(),
        resource.getWorkspaceId());
    // Surface whatever error caused Stairway to begin undoing.
    return flightContext.getResult();
  }
}
