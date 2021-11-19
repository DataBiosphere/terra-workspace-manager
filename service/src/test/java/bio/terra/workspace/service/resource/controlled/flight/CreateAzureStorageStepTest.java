package bio.terra.workspace.service.resource.controlled.flight;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.StepResult;
import bio.terra.workspace.app.configuration.external.AzureConfiguration;
import bio.terra.workspace.common.BaseAzureTest;
import bio.terra.workspace.common.fixtures.ControlledResourceFixtures;
import bio.terra.workspace.generated.model.ApiAzureStorageCreationParameters;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.resource.controlled.flight.create.CreateAzureStorageStep;
import bio.terra.workspace.service.resource.controlled.flight.create.azure.resourcemanager.storage.data.CreateStorageAccountRequestData;
import bio.terra.workspace.service.workspace.model.AzureCloudContext;
import com.azure.core.management.Region;
import com.azure.core.management.exception.ManagementError;
import com.azure.core.management.exception.ManagementException;
import com.azure.core.util.Context;
import com.azure.resourcemanager.storage.StorageManager;
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
  }

  @Test
  void createStorageAccount() throws InterruptedException {

    CreateAzureStorageStep createAzureStorageStep = createCreateAzureStorageStep();
    FlightContext flightContext = createMockFlightContext();
    StepResult stepResult = createAzureStorageStep.doStep(flightContext);

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

    // Verify flight context contains the storage account id.
    assertThat(
        flightContext
            .getWorkingMap()
            .get(CreateAzureStorageStep.CREATED_STORAGE_ACCOUNT_ID, String.class),
        equalTo(STUB_STRING_RETURN));
  }

  @Test
  public void createStorage_failsToCreate() throws InterruptedException {
    FlightContext flightContext = createMockFlightContext();
    CreateAzureStorageStep createAzureStorageStep = createCreateAzureStorageStep();
    when(mockStorageCreateStage.create(any(Context.class))).thenThrow(mockException);

    StepResult stepResult = createAzureStorageStep.doStep(flightContext);

    // Verify step fails...
    assertThat(stepResult.isSuccess(), equalTo(false));

    // Verify storage account is not set in flight context...
    assertThat(
        flightContext
            .getWorkingMap()
            .get(CreateAzureStorageStep.CREATED_STORAGE_ACCOUNT_ID, String.class),
        equalTo(null));
  }

  private CreateAzureStorageStep createCreateAzureStorageStep() {

    CreateAzureStorageStep createAzureStorageStep =
        new CreateAzureStorageStep(
            mockAzureConfig,
            mockAzureCloudContext,
            mockCrlService,
            ControlledResourceFixtures.getAzureStorage(
                creationParameters.getName(), creationParameters.getRegion()));
    return createAzureStorageStep;
  }

  private FlightContext createMockFlightContext() {
    FlightContext mockFlightContext = mock(FlightContext.class);
    when(mockFlightContext.getWorkingMap()).thenReturn(new FlightMap());
    return mockFlightContext;
  }

  @Test
  public void undoStep_twoSimultaneousCreateRequestsFirstSucceeds() throws InterruptedException {

    CreateAzureStorageStep createAzureStorageStep1 = createCreateAzureStorageStep();
    CreateAzureStorageStep createAzureStorageStep2 = createCreateAzureStorageStep();

    FlightContext flightContext1 = createMockFlightContext();
    FlightContext flightContext2 = createMockFlightContext();

    // Call do() for each but fail the second one, and call undo.
    StepResult doResult1 = createAzureStorageStep1.doStep(flightContext1);

    when(mockStorageCreateStage.create(any(Context.class))).thenThrow(mockException);
    StepResult doResult2 = createAzureStorageStep1.doStep(flightContext1);

    StepResult undoResult2 = createAzureStorageStep2.undoStep(flightContext2);

    // Verify first do() returns success but second fails
    assertThat(doResult1, equalTo(StepResult.getStepResultSuccess()));
    assertThat(doResult2.isSuccess(), equalTo(false));

    // Verify second unDo() succeeds
    assertThat(undoResult2, equalTo(StepResult.getStepResultSuccess()));

    // Verify delete operation was not called.
    verify(mockStorageAccounts, times(0)).deleteById(STUB_STRING_RETURN);
  }

  @Test
  public void undoStep_deletesStorageIfStorageAccountIdSetInContext() throws InterruptedException {

    CreateAzureStorageStep createAzureStorageStep = createCreateAzureStorageStep();
    FlightContext flightContext = createMockFlightContext();

    // Set the storage account id in flight context
    flightContext
        .getWorkingMap()
        .put(CreateAzureStorageStep.CREATED_STORAGE_ACCOUNT_ID, STUB_STRING_RETURN);
    StepResult stepResult = createAzureStorageStep.undoStep(flightContext);

    // Verify step returns success
    assertThat(stepResult, equalTo(StepResult.getStepResultSuccess()));

    // Verify the resource is deleted
    verify(mockStorageAccounts).deleteById(STUB_STRING_RETURN);
  }
}
