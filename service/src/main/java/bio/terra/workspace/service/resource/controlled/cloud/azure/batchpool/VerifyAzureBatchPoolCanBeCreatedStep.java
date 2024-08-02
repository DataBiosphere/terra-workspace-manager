package bio.terra.workspace.service.resource.controlled.cloud.azure.batchpool;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.app.configuration.external.AzureConfiguration;
import bio.terra.workspace.generated.model.ApiAzureLandingZoneDeployedResource;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.resource.exception.ResourceNotFoundException;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import bio.terra.workspace.service.workspace.model.AzureCloudContext;
import com.azure.resourcemanager.batch.BatchManager;
import com.azure.resourcemanager.batch.models.BatchAccount;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VerifyAzureBatchPoolCanBeCreatedStep implements Step {
  private static final Logger logger =
      LoggerFactory.getLogger(VerifyAzureBatchPoolCanBeCreatedStep.class);

  private final AzureConfiguration azureConfig;
  private final CrlService crlService;
  private final SamService samService;
  private final LandingZoneBatchAccountFinder landingZoneBatchAccountFinder;
  private final ControlledAzureBatchPoolResource resource;

  public VerifyAzureBatchPoolCanBeCreatedStep(
      AzureConfiguration azureConfig,
      CrlService crlService,
      SamService samService,
      LandingZoneBatchAccountFinder landingZoneBatchAccountFinder,
      ControlledAzureBatchPoolResource resource) {
    this.azureConfig = azureConfig;
    this.crlService = crlService;
    this.samService = samService;
    this.landingZoneBatchAccountFinder = landingZoneBatchAccountFinder;
    this.resource = resource;
  }

  @Override
  public StepResult doStep(FlightContext context) throws InterruptedException, RetryException {
    final AzureCloudContext azureCloudContext =
        context
            .getWorkingMap()
            .get(
                WorkspaceFlightMapKeys.ControlledResourceKeys.AZURE_CLOUD_CONTEXT,
                AzureCloudContext.class);
    final BatchManager batchManager = crlService.getBatchManager(azureCloudContext, azureConfig);

    return validateBatchAccountExists(azureCloudContext, context, batchManager);
  }

  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    return StepResult.getStepResultSuccess();
  }

  private StepResult validateBatchAccountExists(
      AzureCloudContext azureCloudContext, FlightContext context, BatchManager batchManager) {
    Optional<ApiAzureLandingZoneDeployedResource> existingSharedBatchAccount =
        landingZoneBatchAccountFinder.find(samService.getWsmServiceAccountToken(), resource);

    if (existingSharedBatchAccount.isPresent()) {
      BatchAccount batchAccount =
          batchManager.batchAccounts().getById(existingSharedBatchAccount.get().getResourceId());

      logger.info(
          String.format(
              AzureBatchPoolHelper.SHARED_BATCH_ACCOUNT_FOUND_IN_AZURE,
              azureCloudContext.getAzureTenantId(),
              azureCloudContext.getAzureSubscriptionId(),
              azureCloudContext.getAzureResourceGroupId()));
      context
          .getWorkingMap()
          .put(
              WorkspaceFlightMapKeys.ControlledResourceKeys.BATCH_ACCOUNT_NAME,
              batchAccount.name());
      return StepResult.getStepResultSuccess();
    }

    String accountNotFoundMsg =
        String.format(
            AzureBatchPoolHelper.SHARED_BATCH_ACCOUNT_NOT_FOUND_IN_AZURE,
            azureCloudContext.getAzureTenantId(),
            azureCloudContext.getAzureSubscriptionId(),
            azureCloudContext.getAzureResourceGroupId());
    logger.warn(accountNotFoundMsg);
    return new StepResult(
        StepStatus.STEP_RESULT_FAILURE_FATAL, new ResourceNotFoundException(accountNotFoundMsg));
  }
}
