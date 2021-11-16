package bio.terra.workspace.service.resource.controlled.flight.create;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.app.configuration.external.AzureConfiguration;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.resource.controlled.ControlledAzureStorageResource;
import bio.terra.workspace.service.resource.exception.DuplicateResourceException;
import bio.terra.workspace.service.workspace.model.AzureCloudContext;
import com.azure.resourcemanager.storage.StorageManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Gets an Azure Storage Account, and fails if it already exists. This step is designed to run
 * immediately before {@link CreateAzureStorageStep} to ensure idempotency of the create operation.
 */
public class GetAzureStorageStep implements Step {

  private static final Logger logger = LoggerFactory.getLogger(GetAzureStorageStep.class);
  private final AzureConfiguration azureConfig;
  private final CrlService crlService;
  private final ControlledAzureStorageResource resource;
  private final AzureCloudContext azureCloudContext;

  public GetAzureStorageStep(
      AzureConfiguration azureConfig,
      AzureCloudContext azureCloudContext,
      CrlService crlService,
      ControlledAzureStorageResource resource) {
    this.azureConfig = azureConfig;
    this.azureCloudContext = azureCloudContext;
    this.crlService = crlService;
    this.resource = resource;
  }

  @Override
  public StepResult doStep(FlightContext context) throws InterruptedException, RetryException {
    StorageManager storageManager = crlService.getStorageManager(azureCloudContext, azureConfig);

    if (!storageManager
        .storageAccounts()
        .checkNameAvailability(resource.getStorageAccountName())
        .isAvailable()) {
      return StepResult.getStepResultSuccess();
    }

    return new StepResult(
        StepStatus.STEP_RESULT_FAILURE_FATAL,
        new DuplicateResourceException(
            String.format(
                "An Azure Storage Account with name %s already exists",
                resource.getStorageAccountName())));
  }

  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    // Nothing to undo
    return StepResult.getStepResultSuccess();
  }
}
