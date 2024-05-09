package bio.terra.workspace.service.resource.controlled.cloud.azure.batchpool;

import bio.terra.cloudres.azure.resourcemanager.batch.data.CreateBatchPoolRequestData;
import bio.terra.cloudres.azure.resourcemanager.common.Defaults;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.app.configuration.external.AzureConfiguration;
import bio.terra.workspace.common.exception.AzureManagementExceptionUtils;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.resource.controlled.cloud.azure.batchpool.model.BatchPoolUserAssignedManagedIdentity;
import bio.terra.workspace.service.resource.controlled.cloud.azure.managedIdentity.GetManagedIdentityStep;
import bio.terra.workspace.service.resource.controlled.exception.UserAssignedManagedIdentityNotFoundException;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import bio.terra.workspace.service.workspace.model.AzureCloudContext;
import com.azure.core.management.exception.ManagementException;
import com.azure.resourcemanager.batch.BatchManager;
import com.azure.resourcemanager.batch.models.BatchPoolIdentity;
import com.azure.resourcemanager.batch.models.Pool;
import com.azure.resourcemanager.batch.models.PoolIdentityType;
import com.azure.resourcemanager.batch.models.UserAssignedIdentities;
import com.azure.resourcemanager.msi.MsiManager;
import com.azure.resourcemanager.msi.models.Identity;

import java.util.*;

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
    MsiManager msiManager = crlService.getMsiManager(azureCloudContext, azureConfig);

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

    logger.info(String.format("Creating identity for '%s'", GetManagedIdentityStep.getManagedIdentityName(context)));

    final BatchPoolUserAssignedManagedIdentity userAssignedManagedIdentity = new BatchPoolUserAssignedManagedIdentity(
            azureCloudContext.getAzureResourceGroupId(),
            GetManagedIdentityStep.getManagedIdentityName(context),
            null);

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
          configureBatchPool(msiManager, batchPoolDefinition, resource, azureCloudContext, List.of(userAssignedManagedIdentity));
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
    } catch (UserAssignedManagedIdentityNotFoundException e) {
      logger.error(e.getMessage());
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
      if (AzureManagementExceptionUtils.isExceptionCode(
          e, AzureManagementExceptionUtils.RESOURCE_NOT_FOUND)) {
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
          MsiManager msiManager,
          Pool.DefinitionStages.WithCreate batchPoolConfigurable,
          ControlledAzureBatchPoolResource resource,
          AzureCloudContext azureCloudContext,
          List<BatchPoolUserAssignedManagedIdentity> uamiToAssign)
      throws UserAssignedManagedIdentityNotFoundException {

    Optional.ofNullable(resource.getDisplayName())
        .ifPresent(batchPoolConfigurable::withDisplayName);
    Optional.ofNullable(resource.getScaleSettings())
        .ifPresent(batchPoolConfigurable::withScaleSettings);
    Optional.ofNullable(resource.getStartTask()).ifPresent(batchPoolConfigurable::withStartTask);
    Optional.ofNullable(resource.getApplicationPackages())
        .ifPresent(batchPoolConfigurable::withApplicationPackages);
    Optional.ofNullable(resource.getNetworkConfiguration())
        .ifPresent(batchPoolConfigurable::withNetworkConfiguration);
    Optional.ofNullable(resource.getMetadata()).ifPresent(batchPoolConfigurable::withMetadata);

    batchPoolConfigurable =
        configurePoolIdentities(msiManager, batchPoolConfigurable, azureCloudContext, uamiToAssign);
    return batchPoolConfigurable;
  }

  private Pool.DefinitionStages.WithCreate configurePoolIdentities(
      MsiManager msiManager,
      Pool.DefinitionStages.WithCreate batchPoolConfigurable,
      AzureCloudContext azureCloudContext,
      List<BatchPoolUserAssignedManagedIdentity> uamiToAssign)
      throws UserAssignedManagedIdentityNotFoundException {
    if (uamiToAssign != null
        && !uamiToAssign.isEmpty()) {

      Map<String, UserAssignedIdentities> userAssignedIdentitiesMap = new HashMap<>();
      for (var i : uamiToAssign) {
        String resourceGroupName =
            i.resourceGroupName() == null
                ? azureCloudContext.getAzureResourceGroupId()
                : i.resourceGroupName();
        String identityName =
            i.name() == null
                ? getIdentityName(i.clientId(), resourceGroupName, msiManager)
                : i.name();

        // see examples
        // https://github.com/Azure/azure-sdk-for-java/blob/main/sdk/batch/azure-resourcemanager-batch/SAMPLE.md#batchaccount_create
        userAssignedIdentitiesMap.put(
            String.format(
                USER_ASSIGNED_MANAGED_IDENTITY_REFERENCE_TEMPLATE,
                azureCloudContext.getAzureSubscriptionId(),
                resourceGroupName,
                identityName),
            new UserAssignedIdentities());
      }

      batchPoolConfigurable.withIdentity(
          new BatchPoolIdentity()
              .withType(PoolIdentityType.USER_ASSIGNED)
              .withUserAssignedIdentities(userAssignedIdentitiesMap));
    }
    return batchPoolConfigurable;
  }

  private String getIdentityName(UUID clientId, String resourceGroupName, MsiManager msiManager)
      throws UserAssignedManagedIdentityNotFoundException {
    Optional<Identity> identity =
        msiManager.identities().listByResourceGroup(resourceGroupName).stream()
            .filter(i -> i.clientId().equals(clientId.toString()))
            .findFirst();
    if (identity.isEmpty()) {
      throw new UserAssignedManagedIdentityNotFoundException(
          String.format(
              "Managed user assigned identity with clientId='%s' not found in the resource group with name='%s'",
              clientId.toString(), resourceGroupName));
    }
    return identity.get().name();
  }
}
