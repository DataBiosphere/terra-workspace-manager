package bio.terra.workspace.service.resource.controlled.cloud.azure.storage;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import bio.terra.stairway.StepResult;
import bio.terra.workspace.common.fixtures.ControlledResourceFixtures;
import bio.terra.workspace.generated.model.ApiAzureStorageCreationParameters;
import bio.terra.workspace.service.resource.controlled.cloud.azure.storage.resourcemanager.data.CreateStorageAccountRequestData;
import com.azure.core.management.Region;
import com.azure.core.util.Context;
import com.azure.resourcemanager.storage.models.CheckNameAvailabilityResult;
import com.azure.resourcemanager.storage.models.StorageAccount;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

public class CreateAzureStorageStepTest extends BaseStorageStepTest {

  @Mock private CheckNameAvailabilityResult mockNameAvailabilityResult;
  @Mock private StorageAccount.DefinitionStages.Blank mockStorageBlankStage;
  @Mock private StorageAccount.DefinitionStages.WithGroup mockStorageGroupStage;
  @Mock private StorageAccount.DefinitionStages.WithCreate mockStorageCreateStage;
  @Mock private StorageAccountKeyProvider mockStorageAccountKeyProvider;

  private ApiAzureStorageCreationParameters creationParameters;
  private ArgumentCaptor<Context> contextCaptor = ArgumentCaptor.forClass(Context.class);

  @BeforeEach
  public void setup() {
    super.setup();

    // Creation stages mocks
    when(mockStorageAccounts.define(anyString())).thenReturn(mockStorageBlankStage);
    when(mockStorageBlankStage.withRegion(anyString())).thenReturn(mockStorageGroupStage);
    when(mockStorageGroupStage.withExistingResourceGroup(anyString()))
        .thenReturn(mockStorageCreateStage);
    when(mockStorageCreateStage.withHnsEnabled(true)).thenReturn(mockStorageCreateStage);
    when(mockStorageCreateStage.withTag(anyString(), anyString()))
        .thenReturn(mockStorageCreateStage);
    when(mockStorageCreateStage.create(any(Context.class))).thenReturn(mockStorageAccount);
    when(mockStorageAccount.id()).thenReturn(STUB_STRING_RETURN);
    creationParameters = ControlledResourceFixtures.getAzureStorageCreationParameters();
  }

  @Test
  void createStorageAccount() throws InterruptedException {

    CreateAzureStorageStep createAzureStorageStep = createCreateAzureStorageStep();
    StepResult stepResult = createAzureStorageStep.doStep(mockFlightContext);

    // Verify step returns success
    assertThat(stepResult, equalTo(StepResult.getStepResultSuccess()));

    // Verify Azure create call was made correctly
    verify(mockStorageCreateStage).create(contextCaptor.capture());
    Context context = contextCaptor.getValue();

    Optional<CreateStorageAccountRequestData> storageAccountRequestDataOpt =
        context.getValues().values().stream()
            .filter(CreateStorageAccountRequestData.class::isInstance)
            .map(CreateStorageAccountRequestData.class::cast)
            .findFirst();

    CreateStorageAccountRequestData expected =
        CreateStorageAccountRequestData.builder()
            .setName(creationParameters.getStorageAccountName())
            .setRegion(Region.fromName(creationParameters.getRegion()))
            .setResourceGroupName(mockAzureCloudContext.getAzureResourceGroupId())
            .build();

    assertThat(storageAccountRequestDataOpt, equalTo(Optional.of(expected)));
  }

  @Test
  public void createStorage_failsToCreate() throws InterruptedException {
    CreateAzureStorageStep createAzureStorageStep = createCreateAzureStorageStep();
    when(mockStorageCreateStage.create(any(Context.class))).thenThrow(resourceNotFoundException);

    StepResult stepResult = createAzureStorageStep.doStep(mockFlightContext);

    // Verify step fails...
    assertThat(stepResult.isSuccess(), equalTo(false));
  }

  private CreateAzureStorageStep createCreateAzureStorageStep() {

    return new CreateAzureStorageStep(
        mockAzureConfig,
        mockCrlService,
        ControlledResourceFixtures.getAzureStorage(
            creationParameters.getStorageAccountName(), creationParameters.getRegion()),
        mockStorageAccountKeyProvider);
  }

  @Test
  public void undoStep_doesNotDeleteStorageIfStorageAccountDoesNotExist()
      throws InterruptedException {

    CreateAzureStorageStep createAzureStorageStep = createCreateAzureStorageStep();

    // Storage account does not exist
    when(mockNameAvailabilityResult.isAvailable()).thenReturn(true);
    when(mockStorageAccounts.checkNameAvailability(creationParameters.getStorageAccountName()))
        .thenReturn(mockNameAvailabilityResult);

    StepResult stepResult = createAzureStorageStep.undoStep(mockFlightContext);

    // Verify step returns success
    assertThat(stepResult, equalTo(StepResult.getStepResultSuccess()));

    // Verify delete operation is not called.
    verify(mockStorageAccounts, times(0))
        .deleteByResourceGroup(
            mockAzureCloudContext.getAzureResourceGroupId(),
            creationParameters.getStorageAccountName());
  }

  @Test
  public void undoStep_deletesStorageIfStorageAccountExists() throws InterruptedException {

    CreateAzureStorageStep createAzureStorageStep = createCreateAzureStorageStep();

    // Storage account exists
    when(mockNameAvailabilityResult.isAvailable()).thenReturn(false);
    when(mockStorageAccounts.checkNameAvailability(creationParameters.getStorageAccountName()))
        .thenReturn(mockNameAvailabilityResult);

    StepResult stepResult = createAzureStorageStep.undoStep(mockFlightContext);

    // Verify step returns success
    assertThat(stepResult, equalTo(StepResult.getStepResultSuccess()));

    // Verify delete operation is called.
    verify(mockStorageAccounts)
        .deleteByResourceGroup(
            mockAzureCloudContext.getAzureResourceGroupId(),
            creationParameters.getStorageAccountName());
  }
}
