package bio.terra.workspace.service.resource.controlled.cloud.azure.storage;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.mockito.Mockito.when;

import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.workspace.common.fixtures.ControlledResourceFixtures;
import bio.terra.workspace.generated.model.ApiAzureStorageCreationParameters;
import bio.terra.workspace.service.resource.exception.DuplicateResourceException;
import com.azure.resourcemanager.storage.models.CheckNameAvailabilityResult;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

public class GetAzureStorageStepTest extends BaseStorageStepTest {

  @Mock private CheckNameAvailabilityResult mockNameAvailabilityResult;

  @Test
  public void getStorageAccount_doesNotExist() throws InterruptedException {
    final ApiAzureStorageCreationParameters creationParameters =
        ControlledResourceFixtures.getAzureStorageCreationParameters();

    GetAzureStorageStep getAzureStorageStep =
        new GetAzureStorageStep(
            mockAzureConfig,
            mockCrlService,
            ControlledResourceFixtures.getAzureStorage(
                creationParameters.getStorageAccountName(), creationParameters.getRegion()));

    when(mockNameAvailabilityResult.isAvailable()).thenReturn(true);
    when(mockStorageAccounts.checkNameAvailability(creationParameters.getStorageAccountName()))
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
            mockCrlService,
            ControlledResourceFixtures.getAzureStorage(
                creationParameters.getStorageAccountName(), creationParameters.getRegion()));

    when(mockNameAvailabilityResult.isAvailable()).thenReturn(false);
    when(mockStorageAccounts.checkNameAvailability(creationParameters.getStorageAccountName()))
        .thenReturn(mockNameAvailabilityResult);

    final StepResult stepResult = getAzureStorageStep.doStep(mockFlightContext);

    // Verify step returns error
    assertThat(stepResult.getStepStatus(), equalTo(StepStatus.STEP_RESULT_FAILURE_FATAL));
    assertThat(stepResult.getException().get(), instanceOf(DuplicateResourceException.class));
  }
}
