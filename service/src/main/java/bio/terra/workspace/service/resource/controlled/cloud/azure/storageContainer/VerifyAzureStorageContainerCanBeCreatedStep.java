package bio.terra.workspace.service.resource.controlled.cloud.azure.storageContainer;

import bio.terra.common.iam.BearerToken;
import bio.terra.landingzone.db.exception.LandingZoneNotFoundException;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.amalgam.landingzone.azure.LandingZoneApiDispatch;
import bio.terra.workspace.app.configuration.external.AzureConfiguration;
import bio.terra.workspace.common.utils.ManagementExceptionUtils;
import bio.terra.workspace.db.ResourceDao;
import bio.terra.workspace.generated.model.ApiAzureLandingZoneDeployedResource;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.resource.controlled.cloud.azure.storage.ControlledAzureStorageResource;
import bio.terra.workspace.service.resource.exception.DuplicateResourceException;
import bio.terra.workspace.service.resource.exception.ResourceNotFoundException;
import bio.terra.workspace.service.resource.model.WsmResource;
import bio.terra.workspace.service.resource.model.WsmResourceType;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import bio.terra.workspace.service.workspace.model.AzureCloudContext;
import com.azure.core.management.exception.ManagementException;
import com.azure.resourcemanager.storage.StorageManager;
import com.azure.resourcemanager.storage.models.StorageAccount;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * If storage account identifier defined it verifies that the storage account exists in the
 * workspace, and that the storage container does not already exist. Otherwise, it verifies that
 * landing zone with shared storage account exist and that the storage container does not already
 * exist. This step is designed to run immediately before {@link CreateAzureStorageContainerStep} to
 * ensure idempotency of the create operation.
 */
public class VerifyAzureStorageContainerCanBeCreatedStep implements Step {
  private static final Logger logger =
      LoggerFactory.getLogger(VerifyAzureStorageContainerCanBeCreatedStep.class);
  private final AzureConfiguration azureConfig;
  private final CrlService crlService;
  private final ResourceDao resourceDao;
  private final ControlledAzureStorageContainerResource resource;
  private final LandingZoneApiDispatch landingZoneApiDispatch;
  private final AuthenticatedUserRequest userRequest;

  public VerifyAzureStorageContainerCanBeCreatedStep(
      AzureConfiguration azureConfig,
      CrlService crlService,
      ResourceDao resourceDao,
      LandingZoneApiDispatch landingZoneApiDispatch,
      AuthenticatedUserRequest userRequest,
      ControlledAzureStorageContainerResource resource) {
    this.azureConfig = azureConfig;
    this.crlService = crlService;
    this.resourceDao = resourceDao;
    this.landingZoneApiDispatch = landingZoneApiDispatch;
    this.userRequest = userRequest;
    this.resource = resource;
  }

  @Override
  public StepResult doStep(FlightContext context) throws InterruptedException, RetryException {
    final AzureCloudContext azureCloudContext =
        context
            .getWorkingMap()
            .get(ControlledResourceKeys.AZURE_CLOUD_CONTEXT, AzureCloudContext.class);
    final StorageManager storageManager =
        crlService.getStorageManager(azureCloudContext, azureConfig);

    StepResult stepResult =
        validateStorageAccountExists(context, azureCloudContext, storageManager);
    if (!stepResult.isSuccess()) {
      return stepResult;
    }
    return validateStorageContainerDoesntExist(context, azureCloudContext, storageManager);
  }

  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    // Nothing to undo
    return StepResult.getStepResultSuccess();
  }

  private StepResult validateStorageAccountExists(
      FlightContext context, AzureCloudContext azureCloudContext, StorageManager storageManager) {
    return resource.getStorageAccountId() != null
        ? validateWorkspaceStorageAccountExists(context, azureCloudContext, storageManager)
        : validateLandingZoneSharedStorageAccountExist(context, azureCloudContext, storageManager);
  }

  private StepResult validateWorkspaceStorageAccountExists(
      FlightContext context, AzureCloudContext azureCloudContext, StorageManager storageManager) {
    try {
      final WsmResource wsmResource =
          resourceDao.getResource(resource.getWorkspaceId(), resource.getStorageAccountId());
      final ControlledAzureStorageResource storageAccount =
          wsmResource
              .castToControlledResource()
              .castByEnum(WsmResourceType.CONTROLLED_AZURE_STORAGE_ACCOUNT);

      context
          .getWorkingMap()
          .put(ControlledResourceKeys.STORAGE_ACCOUNT_NAME, storageAccount.getStorageAccountName());

      storageManager
          .storageAccounts()
          .getByResourceGroup(
              azureCloudContext.getAzureResourceGroupId(), storageAccount.getStorageAccountName());
    } catch (
        ResourceNotFoundException resourceNotFoundException) { // Thrown by resourceDao.getResource
      return new StepResult(
          StepStatus.STEP_RESULT_FAILURE_FATAL,
          new ResourceNotFoundException(
              String.format(
                  "The storage account with ID '%s' cannot be retrieved from the WSM resource manager.",
                  resource.getStorageAccountId())));
    } catch (ManagementException managementException) { // Thrown by storageManager
      if (ManagementExceptionUtils.isExceptionCode(
          managementException, ManagementExceptionUtils.RESOURCE_NOT_FOUND)) {
        return new StepResult(
            StepStatus.STEP_RESULT_FAILURE_FATAL,
            new ResourceNotFoundException(
                String.format(
                    "The storage account with ID '%s' does not exist in Azure.",
                    resource.getStorageAccountId())));
      }
      logger.warn(
          "Attempt to retrieve storage account with ID '{}' from Azure failed on this try. Error Code: {}.",
          resource.getStorageAccountId(),
          managementException.getValue().getCode(),
          managementException);
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY, managementException);
    }
    return StepResult.getStepResultSuccess();
  }

  private StepResult validateLandingZoneSharedStorageAccountExist(
      FlightContext context, AzureCloudContext azureCloudContext, StorageManager storageManager) {
    try {
      UUID landingZoneId =
          landingZoneApiDispatch.getLandingZoneId(
              new BearerToken(userRequest.getRequiredToken()), resource.getWorkspaceId());
      Optional<ApiAzureLandingZoneDeployedResource> existingSharedStorageAccount =
          landingZoneApiDispatch.getSharedStorageAccount(
              new BearerToken(userRequest.getRequiredToken()), landingZoneId);
      if (existingSharedStorageAccount.isPresent()) {
        StorageAccount storageAccount =
            storageManager
                .storageAccounts()
                .getById(existingSharedStorageAccount.get().getResourceId());

        context
            .getWorkingMap()
            .put(ControlledResourceKeys.STORAGE_ACCOUNT_NAME, storageAccount.name());
        return StepResult.getStepResultSuccess();
      }

      return new StepResult(
          StepStatus.STEP_RESULT_FAILURE_FATAL,
          new ResourceNotFoundException(
              String.format(
                  "Shared storage account not found in landing zone. Landing zone ID='%s'.",
                  landingZoneId)));
    } catch (IllegalStateException illegalStateException) { // Thrown by landingZoneApiDispatch
      return new StepResult(
          StepStatus.STEP_RESULT_FAILURE_FATAL,
          new LandingZoneNotFoundException(
              String.format(
                  "Landing zone associated with the Azure cloud context not found. TenantId='%s', SubscriptionId='%s', ResourceGroupId='%s'",
                  azureCloudContext.getAzureTenantId(),
                  azureCloudContext.getAzureSubscriptionId(),
                  azureCloudContext.getAzureResourceGroupId())));
    }
  }

  private StepResult validateStorageContainerDoesntExist(
      FlightContext context, AzureCloudContext azureCloudContext, StorageManager storageManager) {
    try {
      final String storageAccountName =
          context.getWorkingMap().get(ControlledResourceKeys.STORAGE_ACCOUNT_NAME, String.class);

      storageManager
          .blobContainers()
          .get(
              azureCloudContext.getAzureResourceGroupId(),
              storageAccountName,
              resource.getStorageContainerName());
      return new StepResult(
          StepStatus.STEP_RESULT_FAILURE_FATAL,
          new DuplicateResourceException(
              String.format(
                  "An Azure Storage Container with name '%s' already exists in storage account '%s'",
                  resource.getStorageContainerName(), storageAccountName)));

    } catch (ManagementException e) {
      if (ManagementExceptionUtils.isExceptionCode(
          e, ManagementExceptionUtils.CONTAINER_NOT_FOUND)) {
        return StepResult.getStepResultSuccess();
      }
      logger.warn(
          "Attempt to retrieve storage container '{}' from Azure failed on this try. Error Code: {}.",
          resource.getStorageContainerName(),
          e.getValue().getCode(),
          e);
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY, e);
    }
  }
}
