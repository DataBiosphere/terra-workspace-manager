package bio.terra.workspace.service.resource.controlled.cloud.azure.batchpool;

import bio.terra.cloudres.azure.resourcemanager.batch.data.CreateBatchPoolRequestData;
import bio.terra.cloudres.azure.resourcemanager.common.Defaults;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.app.configuration.external.AzureConfiguration;
import bio.terra.workspace.common.utils.ManagementExceptionUtils;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import bio.terra.workspace.service.workspace.model.AzureCloudContext;
import com.azure.core.management.exception.ManagementException;
import com.azure.resourcemanager.batch.BatchManager;
import com.azure.resourcemanager.batch.models.BatchPoolIdentity;
import com.azure.resourcemanager.batch.models.Pool;
import com.azure.resourcemanager.batch.models.PoolIdentityType;
import com.azure.resourcemanager.batch.models.UserAssignedIdentities;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CreateAzureBatchPoolStep implements Step {
  private static final Logger logger = LoggerFactory.getLogger(CreateAzureBatchPoolStep.class);

  public static final String USER_ASSIGNED_MANAGED_IDENTITY_REFERENCE_TEMPLATE =
      "/subscriptions/%s/resourceGroups/%s/providers/Microsoft.ManagedIdentity/userAssignedIdentities/%s";

  private final AzureConfiguration azureConfig;
  private final CrlService crlService;
  private final ControlledAzureBatchPoolResource resource;

  public CreateAzureBatchPoolStep(
      AzureConfiguration azureConfig,
      CrlService crlService,
      ControlledAzureBatchPoolResource resource) {
    this.azureConfig = azureConfig;
    this.crlService = crlService;
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

    // The batch account name is stored by VerifyAzureBatchPoolCanBeCreated.
    // It can be landing zone shared batch account only
    final String batchAccountName =
        context
            .getWorkingMap()
            .get(WorkspaceFlightMapKeys.ControlledResourceKeys.BATCH_ACCOUNT_NAME, String.class);
    if (batchAccountName == null) {
      logger.error(
          "The batch account name has not been added to the working map. "
              + "VerifyAzureBatchPoolCanBeCreated must be executed first.");
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_FATAL);
    }

    try {
      var batchPoolDefinition =
          batchManager
              .pools()
              .define(resource.getId())
              .withExistingBatchAccount(
                  azureCloudContext.getAzureResourceGroupId(), batchAccountName)
              .withVmSize(resource.getVmSize())
              .withDeploymentConfiguration(resource.getDeploymentConfiguration());

      batchPoolDefinition =
          configureBatchPool(
              batchPoolDefinition, resource, azureCloudContext.getAzureSubscriptionId());
      batchPoolDefinition.create(
          Defaults.buildContext(
              CreateBatchPoolRequestData.builder()
                  .setVmSize(resource.getVmSize())
                  .setId(resource.getId())
                  .setBatchAccountName(batchAccountName)
                  .setResourceGroupName(azureCloudContext.getAzureResourceGroupId())
                  .setTenantId(azureCloudContext.getAzureTenantId())
                  .setSubscriptionId(azureCloudContext.getAzureSubscriptionId())
                  .build()));
      logger.info(
          "Successfully created Azure Batch Pool '{}' in batch account '{}'",
          resource.getId(),
          batchAccountName);
    } catch (ManagementException e) {
      logger.error(
          "Failed to create the Azure Batch Pool '{}' with batch account with the name '{}'. Error Code: {}",
          resource.getId(),
          batchAccountName,
          e.getValue().getCode(),
          e);
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_FATAL, e);
    }
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    final AzureCloudContext azureCloudContext =
        context
            .getWorkingMap()
            .get(
                WorkspaceFlightMapKeys.ControlledResourceKeys.AZURE_CLOUD_CONTEXT,
                AzureCloudContext.class);
    BatchManager batchManager = crlService.getBatchManager(azureCloudContext, azureConfig);

    final String batchAccountName =
        context
            .getWorkingMap()
            .get(WorkspaceFlightMapKeys.ControlledResourceKeys.BATCH_ACCOUNT_NAME, String.class);

    try {
      logger.info(
          "Attempting to delete Azure Batch Pool '{}' in batch account '{}'",
          resource.getId(),
          batchAccountName);

      batchManager
          .pools()
          .delete(azureCloudContext.getAzureResourceGroupId(), batchAccountName, resource.getId());

      logger.info(
          "Successfully deleted Azure Batch Pool '{}' in batch account '{}'",
          resource.getId(),
          batchAccountName);
    } catch (ManagementException e) {
      if (ManagementExceptionUtils.isExceptionCode(
          e, ManagementExceptionUtils.RESOURCE_NOT_FOUND)) {
        logger.info(
            "Azure Batch Pool '{}' in batch account '{}' already deleted",
            resource.getId(),
            batchAccountName);
        return StepResult.getStepResultSuccess();
      }
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_FATAL, e);
    }
    return StepResult.getStepResultSuccess();
  }

  private Pool.DefinitionStages.WithCreate configureBatchPool(
      Pool.DefinitionStages.WithCreate batchPoolConfigurable,
      ControlledAzureBatchPoolResource resource,
      String subscriptionId) {

    Optional.ofNullable(resource.getDisplayName())
        .ifPresent(batchPoolConfigurable::withDisplayName);

    if (resource.getUserAssignedIdentities() != null
        && !resource.getUserAssignedIdentities().isEmpty()) {
      // see examples
      // https://github.com/Azure/azure-sdk-for-java/blob/main/sdk/batch/azure-resourcemanager-batch/SAMPLE.md#batchaccount_create

      Map<String, UserAssignedIdentities> userAssignedIdentitiesMap =
          resource.getUserAssignedIdentities().stream()
              .map(
                  i ->
                      Map.entry(
                          String.format(
                              USER_ASSIGNED_MANAGED_IDENTITY_REFERENCE_TEMPLATE,
                              subscriptionId,
                              i.resourceGroupName(),
                              i.name()),
                          new UserAssignedIdentities()))
              .collect(
                  Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (prev, next) -> prev));

      batchPoolConfigurable.withIdentity(
          new BatchPoolIdentity()
              .withType(PoolIdentityType.USER_ASSIGNED)
              .withUserAssignedIdentities(userAssignedIdentitiesMap));
    }

    Optional.ofNullable(resource.getScaleSettings())
        .ifPresent(batchPoolConfigurable::withScaleSettings);
    Optional.ofNullable(resource.getStartTask()).ifPresent(batchPoolConfigurable::withStartTask);
    Optional.ofNullable(resource.getApplicationPackages())
        .ifPresent(batchPoolConfigurable::withApplicationPackages);
    Optional.ofNullable(resource.getNetworkConfiguration())
        .ifPresent(batchPoolConfigurable::withNetworkConfiguration);
    return batchPoolConfigurable;
  }
}
