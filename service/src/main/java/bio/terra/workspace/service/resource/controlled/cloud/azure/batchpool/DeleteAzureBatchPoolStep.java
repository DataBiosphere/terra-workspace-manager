package bio.terra.workspace.service.resource.controlled.cloud.azure.batchpool;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.app.configuration.external.AzureConfiguration;
import bio.terra.workspace.common.exception.AzureManagementExceptionUtils;
import bio.terra.workspace.generated.model.ApiAzureLandingZoneDeployedResource;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.job.JobMapKeys;
import bio.terra.workspace.service.resource.controlled.cloud.azure.DeleteAzureControlledResourceStep;
import bio.terra.workspace.service.resource.exception.ResourceNotFoundException;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import bio.terra.workspace.service.workspace.model.AzureCloudContext;
import com.azure.core.management.exception.ManagementException;
import com.azure.resourcemanager.batch.BatchManager;
import com.azure.resourcemanager.batch.models.BatchAccount;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DeleteAzureBatchPoolStep extends DeleteAzureControlledResourceStep {
  private static final Logger logger = LoggerFactory.getLogger(DeleteAzureBatchPoolStep.class);

  private final AzureConfiguration azureConfig;
  private final CrlService crlService;
  private final LandingZoneBatchAccountFinder landingZoneBatchAccountFinder;
  private final ControlledAzureBatchPoolResource resource;

  public DeleteAzureBatchPoolStep(
      AzureConfiguration azureConfig,
      CrlService crlService,
      LandingZoneBatchAccountFinder landingZoneBatchAccountFinder,
      ControlledAzureBatchPoolResource resource) {
    this.azureConfig = azureConfig;
    this.crlService = crlService;
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
    BatchManager batchManager = crlService.getBatchManager(azureCloudContext, azureConfig);

    Optional<String> batchAccountName =
        getBatchAccountName(azureCloudContext, context, batchManager);
    if (batchAccountName.isEmpty()) {
      return new StepResult(
          StepStatus.STEP_RESULT_FAILURE_FATAL,
          new ResourceNotFoundException(
              String.format(
                  AzureBatchPoolHelper.SHARED_BATCH_ACCOUNT_NOT_FOUND_IN_AZURE,
                  azureCloudContext.getAzureTenantId(),
                  azureCloudContext.getAzureSubscriptionId(),
                  azureCloudContext.getAzureResourceGroupId())));
    }

    try {
      batchManager
          .pools()
          .delete(
              azureCloudContext.getAzureResourceGroupId(),
              batchAccountName.get(),
              resource.getId());
      logger.info(
          "Successfully deleted Azure Batch Pool '{}' in batch account '{}'",
          resource.getId(),
          batchAccountName.get());
      return StepResult.getStepResultSuccess();
    } catch (ManagementException e) {
      if (AzureManagementExceptionUtils.isExceptionCode(
          e, AzureManagementExceptionUtils.RESOURCE_NOT_FOUND)) {
        logger.info(
            "Azure Batch Pool '{}' in batch account '{}' already deleted",
            resource.getId(),
            batchAccountName.get());
        return StepResult.getStepResultSuccess();
      }
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_FATAL, e);
    }
  }

  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    logger.error(
        "Cannot undo delete of Azure Batch Pool resource {} in workspace {}.",
        resource.getResourceId(),
        resource.getWorkspaceId());
    return context.getResult();
  }

  private Optional<String> getBatchAccountName(
      AzureCloudContext azureCloudContext, FlightContext context, BatchManager batchManager) {
    AuthenticatedUserRequest userRequest =
        context
            .getInputParameters()
            .get(JobMapKeys.AUTH_USER_INFO.getKeyName(), AuthenticatedUserRequest.class);

    Optional<ApiAzureLandingZoneDeployedResource> existingSharedBatchAccount =
        landingZoneBatchAccountFinder.find(userRequest.getRequiredToken(), resource);

    if (existingSharedBatchAccount.isPresent()) {
      BatchAccount batchAccount =
          batchManager.batchAccounts().getById(existingSharedBatchAccount.get().getResourceId());
      return Optional.of(batchAccount.name());
    }

    logger.warn(
        String.format(
            AzureBatchPoolHelper.SHARED_BATCH_ACCOUNT_NOT_FOUND_IN_AZURE,
            azureCloudContext.getAzureTenantId(),
            azureCloudContext.getAzureSubscriptionId(),
            azureCloudContext.getAzureResourceGroupId()));
    return Optional.empty();
  }
}
