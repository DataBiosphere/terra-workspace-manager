package bio.terra.workspace.service.resource.controlled.flight.clone.azure.database;

import bio.terra.cloudres.azure.resourcemanager.common.Defaults;
import bio.terra.cloudres.azure.resourcemanager.storage.data.CreateStorageContainerRequestData;
import bio.terra.common.iam.BearerToken;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.amalgam.landingzone.azure.LandingZoneApiDispatch;
import bio.terra.workspace.app.configuration.external.AzureConfiguration;
import bio.terra.workspace.generated.model.ApiAzureLandingZoneDeployedResource;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.resource.controlled.cloud.azure.storageContainer.ControlledAzureStorageContainerResource;
import bio.terra.workspace.service.resource.controlled.cloud.azure.storageContainer.CreateAzureStorageContainerStep;
import bio.terra.workspace.service.workspace.WorkspaceService;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import bio.terra.workspace.service.workspace.model.AzureCloudContext;
import com.azure.core.management.exception.ManagementException;
import com.azure.resourcemanager.storage.StorageManager;
import com.azure.resourcemanager.storage.models.PublicAccess;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.UUID;

import static bio.terra.workspace.service.resource.controlled.cloud.azure.AzureUtils.getResourceName;

public class CreatePrivateAzureStorageContainerStep implements Step {
    private static final Logger logger =
            LoggerFactory.getLogger(CreateAzureStorageContainerStep.class);
    private final AzureConfiguration azureConfig;
    private final CrlService crlService;
    private final SamService samService;
    private final LandingZoneApiDispatch landingZoneApiDispatch;
    private final WorkspaceService workspaceService;

    public CreatePrivateAzureStorageContainerStep(AzureConfiguration azureConfig, CrlService crlService, LandingZoneApiDispatch landingZoneApiDispatch, WorkspaceService workspaceService, SamService samService) {
        this.azureConfig = azureConfig;
        this.crlService = crlService;
        this.landingZoneApiDispatch = landingZoneApiDispatch;
        this.workspaceService = workspaceService;
        this.samService = samService;
    }

    @Override
    public StepResult doStep(FlightContext context) throws InterruptedException, RetryException {
        BearerToken bearerToken = new BearerToken(samService.getWsmServiceAccountToken());
        UUID workspaceUuid = context.getWorkingMap().get(WorkspaceFlightMapKeys.ControlledResourceKeys.DESTINATION_WORKSPACE_ID, UUID.class);
        final AzureCloudContext azureCloudContext =
                context
                        .getWorkingMap()
                        .get(WorkspaceFlightMapKeys.ControlledResourceKeys.AZURE_CLOUD_CONTEXT, AzureCloudContext.class);

        UUID landingZoneId =
                landingZoneApiDispatch.getLandingZoneId(
                        bearerToken, workspaceService.getWorkspace(workspaceUuid));

        String containerName = "azure-dump-container-%s".formatted(UUID.randomUUID().toString());
        context.getWorkingMap().put(WorkspaceFlightMapKeys.ControlledResourceKeys.AZURE_DB_DUMP_STORAGE_CONTAINER_NAME, containerName);

        final StorageManager storageManager =
                crlService.getStorageManager(azureCloudContext, azureConfig);

        Optional<ApiAzureLandingZoneDeployedResource> storageAccount = landingZoneApiDispatch.getSharedStorageAccount(bearerToken, landingZoneId);


        // The storage account name is stored by VerifyAzureStorageContainerCanBeCreated.
        // It can be workspace managed storage account or landing zone shared storage account
        final String storageAccountName = storageAccount.get().getResourceName();

        if (storageAccountName == null) {
            logger.error(
                    "The storage account name has not been added to the working map. "
                            + "VerifyAzureStorageContainerCanBeCreated must be executed first.");
            return new StepResult(StepStatus.STEP_RESULT_FAILURE_FATAL);
        }

        try {
            storageManager
                    .blobContainers()
                    .defineContainer(containerName)
                    .withExistingStorageAccount(
                            azureCloudContext.getAzureResourceGroupId(), storageAccountName)
                    .withPublicAccess(PublicAccess.NONE)
                    .create(
                            Defaults.buildContext(
                                    CreateStorageContainerRequestData.builder()
                                            .setStorageContainerName(containerName)
                                            .setStorageAccountName(storageAccountName)
                                            .setResourceGroupName(azureCloudContext.getAzureResourceGroupId())
                                            .setSubscriptionId(azureCloudContext.getAzureSubscriptionId())
                                            .setTenantId(azureCloudContext.getAzureTenantId())
                                            .build()));
        } catch (ManagementException e) {
            logger.error(
                    "Failed to create the Azure storage container '{}'. Error Code: {}",
                    containerName,
                    e.getValue().getCode(),
                    e);

            return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY, e);
        }

        return StepResult.getStepResultSuccess();
    }

    @Override
    public StepResult undoStep(FlightContext context) throws InterruptedException {
        return null;
    }
}
