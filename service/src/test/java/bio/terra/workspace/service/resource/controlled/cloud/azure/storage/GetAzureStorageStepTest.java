package bio.terra.workspace.service.resource.controlled.cloud.azure.storage;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.mockito.Mockito.when;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.workspace.app.configuration.external.AzureConfiguration;
import bio.terra.workspace.common.BaseAzureTest;
import bio.terra.workspace.common.fixtures.ControlledResourceFixtures;
import bio.terra.workspace.generated.model.ApiAzureStorageCreationParameters;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.resource.exception.DuplicateResourceException;
import bio.terra.workspace.service.workspace.model.AzureCloudContext;
import com.azure.core.management.exception.ManagementError;
import com.azure.core.management.exception.ManagementException;
import com.azure.resourcemanager.storage.StorageManager;
import com.azure.resourcemanager.storage.models.CheckNameAvailabilityResult;
import com.azure.resourcemanager.storage.models.StorageAccounts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("azure")
public class GetAzureStorageStepTest extends BaseAzureTest {

  private static final String STUB_STRING_RETURN = "stubbed-return";

  @Mock private FlightContext mockFlightContext;
  @Mock private CrlService mockCrlService;
  @Mock private AzureConfiguration mockAzureConfig;
  @Mock private AzureCloudContext mockAzureCloudContext;
  @Mock private StorageManager mockStorageManager;
  @Mock private ManagementException mockException;
  @Mock private CheckNameAvailabilityResult mockNameAvailabilityResult;
  @Mock private StorageAccounts mockStorageAccounts;

  @BeforeEach
  public void setup() {
    when(mockAzureCloudContext.getAzureResourceGroupId()).thenReturn(STUB_STRING_RETURN);
    when(mockCrlService.getStorageManager(mockAzureCloudContext, mockAzureConfig))
        .thenReturn(mockStorageManager);

    when(mockStorageManager.storageAccounts()).thenReturn(mockStorageAccounts);

    when(mockException.getValue())
        .thenReturn(new ManagementError("ResourceNotFound", "Resource was not found."));
  }

  @Test
  public void getStorageAccount_doesNotExist() throws InterruptedException {
    final ApiAzureStorageCreationParameters creationParameters =
        ControlledResourceFixtures.getAzureStorageCreationParameters();

    GetAzureStorageStep getAzureStorageStep =
        new GetAzureStorageStep(
            mockAzureConfig,
            mockAzureCloudContext,
            mockCrlService,
            ControlledResourceFixtures.getAzureStorage(
                creationParameters.getName(), creationParameters.getRegion()));

    when(mockNameAvailabilityResult.isAvailable()).thenReturn(true);
    when(mockStorageAccounts.checkNameAvailability(creationParameters.getName()))
        .thenReturn(mockNameAvailabilityResult);

    final StepResult stepResult = getAzureStorageStep.doStep(mockFlightContext);

    // Verify step returns success
    assertThat(stepResult, equalTo(StepResult.getStepResultSuccess()));
  }

  @Test
  public void getStorageAccount_alreadyExists() throws InterruptedException {
    final ApiAzureStorageCreationParameters creationParameters =
        ControlledResourceFixtures.getAzureStorageCreationParameters();

    GetAzureStorageStep getAzureStorageStep =
        new GetAzureStorageStep(
            mockAzureConfig,
            mockAzureCloudContext,
            mockCrlService,
            ControlledResourceFixtures.getAzureStorage(
                creationParameters.getName(), creationParameters.getRegion()));

    when(mockNameAvailabilityResult.isAvailable()).thenReturn(false);
    when(mockStorageAccounts.checkNameAvailability(creationParameters.getName()))
        .thenReturn(mockNameAvailabilityResult);

    final StepResult stepResult = getAzureStorageStep.doStep(mockFlightContext);

    // Verify step returns error
    assertThat(stepResult.getStepStatus(), equalTo(StepStatus.STEP_RESULT_FAILURE_FATAL));
    assertThat(stepResult.getException().get(), instanceOf(DuplicateResourceException.class));
  }
}
