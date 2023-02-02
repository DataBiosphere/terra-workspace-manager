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
import bio.terra.workspace.generated.model.ApiAzureLandingZoneDeployedResource;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;

public class VerifyAzureBatchPoolCanBeCreatedStepTest extends BaseBatchPoolTest {
  VerifyAzureBatchPoolCanBeCreatedStep verifyAzureBatchPoolCanBeCreatedStep;

  @Captor ArgumentCaptor<String> keyNameCaptor;
  @Captor ArgumentCaptor<String> keyValueCaptor;

  @Mock LandingZoneBatchAccountFinder mockLandingZoneBatchAccountFinder;

  @BeforeEach
  public void setup() {
    resource = buildDefaultResourceBuilder().build();
    initVerifyStep(resource);
    setupBaseMocks();
  }

  @Test
  public void sharedLzStorageAccountExistsSuccess() throws InterruptedException {
    setupMocks(true);

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

    StepResult stepResult = verifyAzureBatchPoolCanBeCreatedStep.doStep(mockFlightContext);

    assertThat(stepResult.getStepStatus(), equalTo(StepStatus.STEP_RESULT_FAILURE_FATAL));
  }

  private void initVerifyStep(ControlledAzureBatchPoolResource resource) {
    verifyAzureBatchPoolCanBeCreatedStep =
        new VerifyAzureBatchPoolCanBeCreatedStep(
            mockAzureConfig,
            mockCrlService,
            mockAuthenticatedUserRequest,
            mockLandingZoneBatchAccountFinder,
            resource);
  }

  private void setupMocks(boolean batchAccountExists) {
    var landingZoneBatchAccountResource = mock(ApiAzureLandingZoneDeployedResource.class);
    Optional<ApiAzureLandingZoneDeployedResource> batchAccount =
        batchAccountExists ? Optional.of(landingZoneBatchAccountResource) : Optional.empty();
    when(mockLandingZoneBatchAccountFinder.find(any(), eq(resource))).thenReturn(batchAccount);

    when(mockBatchAccount.name()).thenReturn(BATCH_ACCOUNT_NAME);
    when(mockBatchAccounts.getById(any())).thenReturn(mockBatchAccount);
    when(mockBatchManager.batchAccounts()).thenReturn(mockBatchAccounts);
    when(mockCrlService.getBatchManager(any(), any())).thenReturn(mockBatchManager);
  }
}
