package bio.terra.workspace.service.resource.controlled.cloud.azure.storageContainer;

import bio.terra.stairway.StepResult;
import bio.terra.workspace.common.fixtures.ControlledResourceFixtures;
import bio.terra.workspace.generated.model.ApiAzureStorageContainerCreationParameters;
import bio.terra.workspace.service.resource.controlled.cloud.azure.storage.BaseStorageStepTest;
import bio.terra.workspace.service.resource.controlled.cloud.azure.storageContainer.resourcemanager.data.CreateStorageContainerRequestData;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import com.azure.core.management.exception.ManagementError;
import com.azure.core.management.exception.ManagementException;
import com.azure.core.util.Context;
import com.azure.resourcemanager.storage.models.BlobContainer;
import com.azure.resourcemanager.storage.models.BlobContainers;
import com.azure.resourcemanager.storage.models.PublicAccess;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.springframework.test.context.ActiveProfiles;

import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ActiveProfiles("azure")
public class CreateAzureStorageContainerStepTest extends BaseStorageStepTest {

    @Mock private BlobContainers mockBlobContainers;
    @Mock private BlobContainer mockBlobContainer;
    @Mock private BlobContainer.DefinitionStages.Blank mockBlankStage;
    @Mock private BlobContainer.DefinitionStages.WithPublicAccess mockPublicAccessStage;
    @Mock private BlobContainer.DefinitionStages.WithCreate mockCreateStage;
    @Mock private BlobContainer mockStorageContainer;

    final private String storageAccountName = ControlledResourceFixtures.uniqueStorageAccountName();
    final ApiAzureStorageContainerCreationParameters creationParameters =
            ControlledResourceFixtures.getAzureStorageContainerCreationParameters();
    final private ArgumentCaptor<Context> contextCaptor = ArgumentCaptor.forClass(Context.class);
    final private ManagementException containerNotFoundException =
            new ManagementException(
                    "Resource was not found.",
                    /*response=*/ null,
                    new ManagementError("ContainerNotFound", "Container was not found."));

    @BeforeEach
    public void setup() {
        super.setup();

        // Creation stages mocks
        when(mockStorageManager.blobContainers()).thenReturn(mockBlobContainers);
        when(mockBlobContainers.defineContainer(anyString())).thenReturn(mockBlankStage);
        when(mockBlankStage.withExistingStorageAccount(
                STUB_STRING_RETURN, storageAccountName
        )).thenReturn(mockPublicAccessStage);
        when(mockPublicAccessStage.withPublicAccess(PublicAccess.NONE))
                .thenReturn(mockCreateStage);
        when(mockCreateStage.withMetadata(anyString(), anyString())).thenReturn(mockCreateStage);
        when(mockCreateStage.create(any(Context.class))).thenReturn(mockStorageContainer);
    }

    @Test
    void createStorageContainer() throws InterruptedException {

        CreateAzureStorageContainerStep createAzureStorageContainerStep = createCreateAzureStorageContainerStep();
        StepResult stepResult = createAzureStorageContainerStep.doStep(mockFlightContext);

        // Verify step returns success
        assertThat(stepResult, equalTo(StepResult.getStepResultSuccess()));

        // Verify Azure create call was made correctly
        verify(mockCreateStage).create(contextCaptor.capture());
        Context context = contextCaptor.getValue();

        Optional<CreateStorageContainerRequestData> storageContainerRequestDataOpt =
                context.getValues().values().stream()
                        .filter(CreateStorageContainerRequestData.class::isInstance)
                        .map(CreateStorageContainerRequestData.class::cast)
                        .findFirst();

        CreateStorageContainerRequestData expected =
                CreateStorageContainerRequestData.builder()
                        .setStorageContainerName(creationParameters.getStorageContainerName())
                        .setStorageAccountId(creationParameters.getStorageAccountId())
                        .setResourceGroupName(mockAzureCloudContext.getAzureResourceGroupId())
                        .build();

        assertThat(storageContainerRequestDataOpt, equalTo(Optional.of(expected)));
    }

    private CreateAzureStorageContainerStep createCreateAzureStorageContainerStep() {
        when(mockFlightContext.getWorkingMap().get(
                WorkspaceFlightMapKeys.ControlledResourceKeys.STORAGE_ACCOUNT_NAME, String.class)
        ).thenReturn(storageAccountName);

        CreateAzureStorageContainerStep createAzureStorageContainerStep =
                new CreateAzureStorageContainerStep(
                        mockAzureConfig,
                        mockCrlService,
                        ControlledResourceFixtures.getAzureStorageContainer(
                                creationParameters.getStorageAccountId(), creationParameters.getStorageContainerName()));
        return createAzureStorageContainerStep;
    }


    @Test
    public void createStorageContainer_failsToCreate() throws InterruptedException {
        CreateAzureStorageContainerStep createAzureStorageContainerStep = createCreateAzureStorageContainerStep();
        when(mockCreateStage.create(any(Context.class))).thenThrow(resourceNotFoundException);

        StepResult stepResult = createAzureStorageContainerStep.doStep(mockFlightContext);

        // Verify step fails...
        assertThat(stepResult.isSuccess(), equalTo(false));
    }

    @Test
    public void undoStep_doesNotDeleteStorageContainerIfStorageAccountDoesNotExist() throws InterruptedException {

        CreateAzureStorageContainerStep createAzureStorageContainerStep = createCreateAzureStorageContainerStep();

        // Storage account does not exist. We do not try to delete the container in this case.
        when(mockStorageAccounts.getByResourceGroup(mockAzureCloudContext.getAzureResourceGroupId(),
                storageAccountName)).thenThrow(resourceNotFoundException);

        StepResult stepResult = createAzureStorageContainerStep.undoStep(mockFlightContext);

        // Verify step returns success
        assertThat(stepResult, equalTo(StepResult.getStepResultSuccess()));

        // Verify delete operation is not called.
        verify(mockBlobContainers, times(0)).delete(
                mockAzureCloudContext.getAzureResourceGroupId(),
                storageAccountName,
                creationParameters.getStorageContainerName()
        );
    }

    @Test
    public void undoStep_doesNotDeleteStorageContainerIfStorageContainerDoesNotExist() throws InterruptedException {

        CreateAzureStorageContainerStep createAzureStorageContainerStep = createCreateAzureStorageContainerStep();

        // Storage account does exist.
        when(mockStorageAccounts.getByResourceGroup(mockAzureCloudContext.getAzureResourceGroupId(),
                storageAccountName)).thenReturn(mockStorageAccount);

        // Storage container does not exist. We do not try to delete the container in this case.
        when(mockBlobContainers.get(mockAzureCloudContext.getAzureResourceGroupId(), storageAccountName,
                creationParameters.getStorageContainerName())).thenThrow(containerNotFoundException);

        StepResult stepResult = createAzureStorageContainerStep.undoStep(mockFlightContext);

        // Verify step returns success
        assertThat(stepResult, equalTo(StepResult.getStepResultSuccess()));

        // Verify delete operation is not called.
        verify(mockBlobContainers, times(0)).delete(
                mockAzureCloudContext.getAzureResourceGroupId(),
                storageAccountName,
                creationParameters.getStorageContainerName()
        );
    }

    @Test
    public void undoStep_deletesStorageIfStorageAccountExists() throws InterruptedException {

        CreateAzureStorageContainerStep createAzureStorageContainerStep = createCreateAzureStorageContainerStep();

        // Storage account and container exist.
        when(mockStorageAccounts.getByResourceGroup(mockAzureCloudContext.getAzureResourceGroupId(),
                storageAccountName)).thenReturn(mockStorageAccount);
        when(mockBlobContainers.get(mockAzureCloudContext.getAzureResourceGroupId(), storageAccountName,
                creationParameters.getStorageContainerName())).thenReturn(mockBlobContainer);

        StepResult stepResult = createAzureStorageContainerStep.undoStep(mockFlightContext);

        // Verify step returns success
        assertThat(stepResult, equalTo(StepResult.getStepResultSuccess()));

        // Verify delete operation is called.
        verify(mockBlobContainers).delete(
                mockAzureCloudContext.getAzureResourceGroupId(),
                storageAccountName,
                creationParameters.getStorageContainerName()
        );
    }
}
