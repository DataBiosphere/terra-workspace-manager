package bio.terra.workspace.service.resource.controlled.cloud.azure.storageContainer;

import bio.terra.stairway.StepResult;
import bio.terra.workspace.common.fixtures.ControlledResourceFixtures;
import bio.terra.workspace.generated.model.ApiAzureStorageContainerCreationParameters;
import bio.terra.workspace.service.resource.controlled.cloud.azure.storage.BaseStorageStepTest;
import bio.terra.workspace.service.resource.controlled.cloud.azure.storageContainer.resourcemanager.data.CreateStorageContainerRequestData;
import com.azure.core.http.rest.PagedIterable;
import com.azure.core.util.Context;
import com.azure.resourcemanager.storage.fluent.models.ListContainerItemInner;
import com.azure.resourcemanager.storage.models.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.springframework.test.context.ActiveProfiles;

import java.util.Collections;
import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ActiveProfiles("azure")
public class CreateAzureStorageContainerStepTest extends BaseStorageStepTest {

  @Mock private BlobContainers mockBlobContainers;
  @Mock private BlobContainer.DefinitionStages.Blank mockBlankStage;
  @Mock private BlobContainer.DefinitionStages.WithPublicAccess mockPublicAccessStage;
  @Mock private BlobContainer.DefinitionStages.WithCreate mockCreateStage;
  @Mock private BlobContainer mockStorageContainer;
  @Mock private PagedIterable<ListContainerItemInner> mockListResult;
  @Mock private ListContainerItemInner mockContainerResult;

  private ApiAzureStorageContainerCreationParameters creationParameters;
  private ArgumentCaptor<Context> contextCaptor = ArgumentCaptor.forClass(Context.class);

  @BeforeEach
  public void setup() {
    super.setup();
    creationParameters = ControlledResourceFixtures.getAzureStorageContainerCreationParameters();

    // Creation stages mocks
    when(mockStorageManager.blobContainers()).thenReturn(mockBlobContainers);
    when(mockBlobContainers.defineContainer(anyString())).thenReturn(mockBlankStage);
    when(mockBlankStage.withExistingStorageAccount(
            STUB_STRING_RETURN, creationParameters.getStorageAccountName()
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
            .setName(creationParameters.getName())
            .setStorageAccountName(creationParameters.getStorageAccountName())
            .setResourceGroupName(mockAzureCloudContext.getAzureResourceGroupId())
            .build();

    assertThat(storageContainerRequestDataOpt, equalTo(Optional.of(expected)));
  }

  private CreateAzureStorageContainerStep createCreateAzureStorageContainerStep() {

    CreateAzureStorageContainerStep createAzureStorageContainerStep =
            new CreateAzureStorageContainerStep(
                    mockAzureConfig,
                    mockCrlService,
                    ControlledResourceFixtures.getAzureStorageContainer(
                            creationParameters.getStorageAccountName(), creationParameters.getName()));
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
            creationParameters.getStorageAccountName())).thenThrow(resourceNotFoundException);

    StepResult stepResult = createAzureStorageContainerStep.undoStep(mockFlightContext);

    // Verify step returns success
    assertThat(stepResult, equalTo(StepResult.getStepResultSuccess()));

    // Verify delete operation is not called.
    verify(mockBlobContainers, times(0)).delete(
            mockAzureCloudContext.getAzureResourceGroupId(),
            creationParameters.getStorageAccountName(),
            creationParameters.getName()
    );
  }

  @Test
  public void undoStep_doesNotDeleteStorageContainerIfStorageContainerDoesNotExist() throws InterruptedException {

    CreateAzureStorageContainerStep createAzureStorageContainerStep = createCreateAzureStorageContainerStep();

    // Storage account does exist.
    when(mockStorageAccounts.getByResourceGroup(mockAzureCloudContext.getAzureResourceGroupId(),
            creationParameters.getStorageAccountName())).thenReturn(mockStorageAccount);

    // Storage container does not exist. We do not try to delete the container in this case.
    when(mockBlobContainers.list(mockAzureCloudContext.getAzureResourceGroupId(),
            creationParameters.getStorageAccountName()))
            .thenReturn(mockListResult);
    when(mockListResult.iterator()).thenReturn(Collections.emptyIterator());

    StepResult stepResult = createAzureStorageContainerStep.undoStep(mockFlightContext);

    // Verify step returns success
    assertThat(stepResult, equalTo(StepResult.getStepResultSuccess()));

    // Verify delete operation is not called.
    verify(mockBlobContainers, times(0)).delete(
            mockAzureCloudContext.getAzureResourceGroupId(),
            creationParameters.getStorageAccountName(),
            creationParameters.getName()
    );
  }

  @Test
  public void undoStep_deletesStorageIfStorageAccountExists() throws InterruptedException {

    CreateAzureStorageContainerStep createAzureStorageContainerStep = createCreateAzureStorageContainerStep();

    // Storage account and container exist.
    when(mockStorageAccounts.getByResourceGroup(mockAzureCloudContext.getAzureResourceGroupId(),
            creationParameters.getStorageAccountName())).thenReturn(mockStorageAccount);
    when(mockBlobContainers.list(mockAzureCloudContext.getAzureResourceGroupId(), creationParameters.getStorageAccountName()))
            .thenReturn(mockListResult);
    when(mockListResult.iterator()).thenReturn(Collections.singletonList(mockContainerResult).iterator());
    when(mockContainerResult.name()).thenReturn(creationParameters.getName());

    StepResult stepResult = createAzureStorageContainerStep.undoStep(mockFlightContext);

    // Verify step returns success
    assertThat(stepResult, equalTo(StepResult.getStepResultSuccess()));

    // Verify delete operation is called.
    verify(mockBlobContainers).delete(
            mockAzureCloudContext.getAzureResourceGroupId(),
            creationParameters.getStorageAccountName(),
            creationParameters.getName()
      );
  }
}
