package bio.terra.workspace.service.resource.controlled.cloud.azure.storage;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.StepResult;
import bio.terra.workspace.app.configuration.external.AzureConfiguration;
import bio.terra.workspace.common.BaseAzureTest;
import bio.terra.workspace.common.fixtures.ControlledResourceFixtures;
import bio.terra.workspace.generated.model.ApiAzureStorageCreationParameters;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.resource.controlled.cloud.azure.storage.resourcemanager.data.CreateStorageAccountRequestData;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import bio.terra.workspace.service.workspace.model.AzureCloudContext;
import com.azure.core.management.Region;
import com.azure.core.management.exception.ManagementError;
import com.azure.core.management.exception.ManagementException;
import com.azure.core.util.Context;
import com.azure.resourcemanager.storage.StorageManager;
import com.azure.resourcemanager.storage.models.CheckNameAvailabilityResult;
import com.azure.resourcemanager.storage.models.StorageAccount;
import com.azure.resourcemanager.storage.models.StorageAccounts;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("azure")
public class CreateAzureStorageStepTest extends BaseAzureTest {

  private static final String STUB_STRING_RETURN = "stubbed-return";
  private ApiAzureStorageCreationParameters creationParameters;
  @Mock private CrlService mockCrlService;
  @Mock private AzureConfiguration mockAzureConfig;
  @Mock private AzureCloudContext mockAzureCloudContext;
  @Mock private StorageManager mockStorageManager;
  @Mock private ManagementException mockException;
  @Mock private StorageAccounts mockStorageAccounts;
  @Mock private StorageAccount.DefinitionStages.Blank mockStorageBlankStage;
  @Mock private StorageAccount.DefinitionStages.WithGroup mockStorageGroupStage;
  @Mock private StorageAccount.DefinitionStages.WithCreate mockStorageCreateStage;
  @Mock private StorageAccount mockStorageAccount;
  @Mock private CheckNameAvailabilityResult mockNameAvailabilityResult;
  @Mock private FlightContext mockFlightContext;
  @Mock private FlightMap mockWorkingMap;

  private ArgumentCaptor<Context> contextCaptor = ArgumentCaptor.forClass(Context.class);

  @BeforeEach
  public void setup() {

    when(mockAzureCloudContext.getAzureResourceGroupId()).thenReturn(STUB_STRING_RETURN);
    when(mockCrlService.getStorageManager(mockAzureCloudContext, mockAzureConfig))
        .thenReturn(mockStorageManager);

    when(mockStorageManager.storageAccounts()).thenReturn(mockStorageAccounts);

    when(mockException.getValue())
        .thenReturn(new ManagementError("ResourceNotFound", "Resource was not found."));

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

    // Exception mock
    when(mockException.getValue())
        .thenReturn(new ManagementError("Conflict", "Invalid resource state."));
    when(mockFlightContext.getWorkingMap()).thenReturn(mockWorkingMap);
    when(mockWorkingMap.get(ControlledResourceKeys.AZURE_CLOUD_CONTEXT, AzureCloudContext.class)).thenReturn(mockAzureCloudContext);
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
            .setName(creationParameters.getName())
            .setRegion(Region.fromName(creationParameters.getRegion()))
            .setResourceGroupName(mockAzureCloudContext.getAzureResourceGroupId())
            .build();

    assertThat(storageAccountRequestDataOpt, equalTo(Optional.of(expected)));
  }

  @Test
  public void createStorage_failsToCreate() throws InterruptedException {
    CreateAzureStorageStep createAzureStorageStep = createCreateAzureStorageStep();
    when(mockStorageCreateStage.create(any(Context.class))).thenThrow(mockException);

    StepResult stepResult = createAzureStorageStep.doStep(mockFlightContext);

    // Verify step fails...
    assertThat(stepResult.isSuccess(), equalTo(false));
  }

  private CreateAzureStorageStep createCreateAzureStorageStep() {

    CreateAzureStorageStep createAzureStorageStep =
        new CreateAzureStorageStep(
            mockAzureConfig,
            mockCrlService,
            ControlledResourceFixtures.getAzureStorage(
                creationParameters.getName(), creationParameters.getRegion()));
    return createAzureStorageStep;
  }

  @Test
  public void undoStep_deletesStorageIfStorageAccountDoesNotExist() throws InterruptedException {

    CreateAzureStorageStep createAzureStorageStep = createCreateAzureStorageStep();

    // Storage account does not exist
    when(mockNameAvailabilityResult.isAvailable()).thenReturn(true);
    when(mockStorageAccounts.checkNameAvailability(creationParameters.getName()))
        .thenReturn(mockNameAvailabilityResult);

    StepResult stepResult = createAzureStorageStep.undoStep(mockFlightContext);

    // Verify step returns success
    assertThat(stepResult, equalTo(StepResult.getStepResultSuccess()));

    // Verify delete operation is not called.
    verify(mockStorageAccounts, times(0))
        .deleteByResourceGroup(
            mockAzureCloudContext.getAzureResourceGroupId(), creationParameters.getName());
  }

  @Test
  public void undoStep_deletesStorageIfStorageAccountExists() throws InterruptedException {

    CreateAzureStorageStep createAzureStorageStep = createCreateAzureStorageStep();

    // Storage account exists
    when(mockNameAvailabilityResult.isAvailable()).thenReturn(false);
    when(mockStorageAccounts.checkNameAvailability(creationParameters.getName()))
        .thenReturn(mockNameAvailabilityResult);

    StepResult stepResult = createAzureStorageStep.undoStep(mockFlightContext);

    // Verify step returns success
    assertThat(stepResult, equalTo(StepResult.getStepResultSuccess()));

    // Verify delete operation is called.
    verify(mockStorageAccounts)
        .deleteByResourceGroup(
            mockAzureCloudContext.getAzureResourceGroupId(), creationParameters.getName());
  }
}
