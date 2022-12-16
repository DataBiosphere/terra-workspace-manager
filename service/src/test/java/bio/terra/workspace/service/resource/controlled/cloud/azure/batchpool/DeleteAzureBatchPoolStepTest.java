package bio.terra.workspace.service.resource.controlled.cloud.azure.batchpool;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.workspace.app.configuration.external.AzureConfiguration;
import bio.terra.workspace.generated.model.ApiAzureLandingZoneDeployedResource;
import bio.terra.workspace.service.workspace.model.AzureCloudContext;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;

public class DeleteAzureBatchPoolStepTest extends BaseBatchPoolTest {

  @Captor ArgumentCaptor<String> resourceGroupNameCaptor;
  @Captor ArgumentCaptor<String> accountNameCaptor;
  @Captor ArgumentCaptor<String> poolIdCaptor;

  private DeleteAzureBatchPoolStep deleteAzureBatchPoolStep;

  @BeforeEach
  public void setup() {
    resource = buildDefaultResourceBuilder().build();
    initDeleteStep(resource);
    setupBaseMocks();
  }

  @Test
  public void deleteBatchPoolControlledByLzBatchAccountSuccess() throws InterruptedException {
    setupMocks(true);

    StepResult stepResult = deleteAzureBatchPoolStep.doStep(mockFlightContext);

    assertThat(stepResult, equalTo(StepResult.getStepResultSuccess()));

    verify(mockPools, times(1))
        .delete(
            resourceGroupNameCaptor.capture(), accountNameCaptor.capture(), poolIdCaptor.capture());
    assertThat(resourceGroupNameCaptor.getValue(), equalTo(AZURE_CLOUD_CONTEXT_RESOURCE_GROUP_ID));
    assertThat(accountNameCaptor.getValue(), equalTo(BATCH_ACCOUNT_NAME));
    assertThat(poolIdCaptor.getValue(), equalTo(resource.getId()));
  }

  @Test
  public void deleteBatchPoolControlledByLzBatchAccountFailure_NoSharedBatchAccount()
      throws InterruptedException {
    setupMocks(false);

    StepResult stepResult = deleteAzureBatchPoolStep.doStep(mockFlightContext);

    assertThat(stepResult.getStepStatus(), equalTo(StepStatus.STEP_RESULT_FAILURE_FATAL));
  }

  private void initDeleteStep(ControlledAzureBatchPoolResource resource) {
    deleteAzureBatchPoolStep =
        new DeleteAzureBatchPoolStep(
            mockAzureConfig, mockCrlService, mockLandingZoneBatchAccountFinder, resource);
  }

  private void setupMocks(boolean sharedBatchAccountExists) {
    ApiAzureLandingZoneDeployedResource mockApiAzureLandingZoneDeployedResource =
        mock(ApiAzureLandingZoneDeployedResource.class);
    when(mockBatchAccount.name()).thenReturn(BATCH_ACCOUNT_NAME);
    when(mockBatchAccounts.getById(any(String.class))).thenReturn(mockBatchAccount);
    when(mockBatchManager.batchAccounts()).thenReturn(mockBatchAccounts);
    when(mockBatchManager.pools()).thenReturn(mockPools);
    when(mockCrlService.getBatchManager(
            any(AzureCloudContext.class), any(AzureConfiguration.class)))
        .thenReturn(mockBatchManager);

    when(mockApiAzureLandingZoneDeployedResource.getResourceId())
        .thenReturn(resource.getResourceId().toString());
    Optional<ApiAzureLandingZoneDeployedResource> lzSharedBatchAccount =
        sharedBatchAccountExists
            ? Optional.of(mockApiAzureLandingZoneDeployedResource)
            : Optional.empty();
    when(mockLandingZoneBatchAccountFinder.find(any(String.class), eq(resource)))
        .thenReturn(lzSharedBatchAccount);
  }
}
