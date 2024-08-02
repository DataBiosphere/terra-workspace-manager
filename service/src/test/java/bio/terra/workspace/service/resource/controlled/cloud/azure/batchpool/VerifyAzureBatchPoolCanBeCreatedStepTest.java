package bio.terra.workspace.service.resource.controlled.cloud.azure.batchpool;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.workspace.app.configuration.external.AzureConfiguration;
import bio.terra.workspace.generated.model.ApiAzureLandingZoneDeployedResource;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import bio.terra.workspace.service.workspace.model.AzureCloudContext;
import com.azure.resourcemanager.batch.BatchManager;
import com.azure.resourcemanager.batch.models.*;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@Tag("azure-unit")
@ExtendWith(MockitoExtension.class)
public class VerifyAzureBatchPoolCanBeCreatedStepTest {

  @Captor ArgumentCaptor<String> keyNameCaptor;
  @Captor ArgumentCaptor<String> keyValueCaptor;

  public static final String BATCH_ACCOUNT_NAME = "sharedBatchAccount";
  public static final String AZURE_CLOUD_CONTEXT_RESOURCE_GROUP_ID = "resourceGroup";
  public static final String TENANT_ID = "tenantId";
  public static final String SUBSCRIPTION_ID = "subscriptionId";

  @Mock public AzureConfiguration mockAzureConfig;
  @Mock public CrlService mockCrlService;
  @Mock public SamService samService;
  @Mock public LandingZoneBatchAccountFinder mockLandingZoneBatchAccountFinder;
  @Mock public FlightContext mockFlightContext;
  @Mock public FlightMap mockWorkingMap;
  @Mock public BatchManager mockBatchManager;
  @Mock public BatchAccounts mockBatchAccounts;
  @Mock public BatchAccount mockBatchAccount;
  @Mock public AzureCloudContext mockAzureCloudContext;

  @Mock ControlledAzureBatchPoolResource resource;

  @BeforeEach
  public void setup() {
    // setup cloud context
    when(mockAzureCloudContext.getAzureResourceGroupId())
        .thenReturn(AZURE_CLOUD_CONTEXT_RESOURCE_GROUP_ID);
    when(mockAzureCloudContext.getAzureTenantId()).thenReturn(TENANT_ID);
    when(mockAzureCloudContext.getAzureSubscriptionId()).thenReturn(SUBSCRIPTION_ID);
    // setup auth request
    when(samService.getWsmServiceAccountToken()).thenReturn("FAKE_TOKEN");
    // setup flight context
    when(mockWorkingMap.get(
            WorkspaceFlightMapKeys.ControlledResourceKeys.AZURE_CLOUD_CONTEXT,
            AzureCloudContext.class))
        .thenReturn(mockAzureCloudContext);
    when(mockFlightContext.getWorkingMap()).thenReturn(mockWorkingMap);
  }

  @Test
  public void sharedLzStorageAccountExistsSuccess() throws InterruptedException {
    setupMocks(true);
    var verifyAzureBatchPoolCanBeCreatedStep =
        new VerifyAzureBatchPoolCanBeCreatedStep(
            mockAzureConfig,
            mockCrlService,
            samService,
            mockLandingZoneBatchAccountFinder,
            resource);
    StepResult stepResult = verifyAzureBatchPoolCanBeCreatedStep.doStep(mockFlightContext);

    assertThat(stepResult.getStepStatus(), equalTo(StepStatus.STEP_RESULT_SUCCESS));
    verify(mockWorkingMap, times(1)).put(keyNameCaptor.capture(), keyValueCaptor.capture());
    assertThat(
        keyNameCaptor.getValue(),
        equalTo(WorkspaceFlightMapKeys.ControlledResourceKeys.BATCH_ACCOUNT_NAME));
    assertThat(keyValueCaptor.getValue(), equalTo(BATCH_ACCOUNT_NAME));
  }

  @Test
  public void sharedLzStorageAccountDoesntExistFailure() throws InterruptedException {
    setupMocks(false);
    var verifyAzureBatchPoolCanBeCreatedStep =
        new VerifyAzureBatchPoolCanBeCreatedStep(
            mockAzureConfig,
            mockCrlService,
            samService,
            mockLandingZoneBatchAccountFinder,
            resource);
    StepResult stepResult = verifyAzureBatchPoolCanBeCreatedStep.doStep(mockFlightContext);

    assertThat(stepResult.getStepStatus(), equalTo(StepStatus.STEP_RESULT_FAILURE_FATAL));
  }

  private void setupMocks(boolean batchAccountExists) {
    var landingZoneBatchAccountResource = mock(ApiAzureLandingZoneDeployedResource.class);
    Optional<ApiAzureLandingZoneDeployedResource> batchAccount =
        batchAccountExists ? Optional.of(landingZoneBatchAccountResource) : Optional.empty();
    when(mockLandingZoneBatchAccountFinder.find(any(), eq(resource))).thenReturn(batchAccount);

    when(mockCrlService.getBatchManager(any(), any())).thenReturn(mockBatchManager);
    if (batchAccountExists) {
      when(mockBatchAccount.name()).thenReturn(BATCH_ACCOUNT_NAME);
      when(mockBatchAccounts.getById(any())).thenReturn(mockBatchAccount);
      when(mockBatchManager.batchAccounts()).thenReturn(mockBatchAccounts);
    }
  }
}
