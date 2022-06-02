package bio.terra.workspace.service.resource.controlled.cloud.azure.storageContainer;

import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.workspace.common.fixtures.ControlledResourceFixtures;
import bio.terra.workspace.generated.model.ApiAzureStorageContainerCreationParameters;
import bio.terra.workspace.service.resource.controlled.cloud.azure.storage.BaseStorageStepTest;
import bio.terra.workspace.service.resource.exception.DuplicateResourceException;
import bio.terra.workspace.service.resource.exception.ResourceNotFoundException;
import com.azure.core.http.rest.PagedIterable;
import com.azure.resourcemanager.storage.fluent.models.ListContainerItemInner;
import com.azure.resourcemanager.storage.models.BlobContainers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.test.context.ActiveProfiles;

import java.util.Collections;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.mockito.Mockito.when;

@ActiveProfiles("azure")
public class GetAzureStorageContainerStepTest extends BaseStorageStepTest {

  @Mock private BlobContainers mockBlobContainers;
  @Mock private PagedIterable<ListContainerItemInner> mockListResult;
  @Mock private ListContainerItemInner mockContainerResult;

  @BeforeEach
  public void setup() {
    super.setup();
    when(mockStorageManager.blobContainers()).thenReturn(mockBlobContainers);
  }

  @Test
  public void getStorageContainer_containerDoesNotExist() throws InterruptedException {
    final ApiAzureStorageContainerCreationParameters creationParameters =
        ControlledResourceFixtures.getAzureStorageContainerCreationParameters();

    GetAzureStorageContainerStep getAzureStorageContainerStep =
        new GetAzureStorageContainerStep(
            mockAzureConfig,
            mockCrlService,
            ControlledResourceFixtures.getAzureStorageContainer(
                creationParameters.getStorageAccountName(), creationParameters.getName()));

    // In order to be able to create a storage container, a storage account with the name must already exist
    // (so availability of the name is false because it is in use).
    when(mockNameAvailabilityResult.isAvailable()).thenReturn(false);
    when(mockStorageAccounts.checkNameAvailability(creationParameters.getStorageAccountName()))
        .thenReturn(mockNameAvailabilityResult);

    // The storage container must not already exist.
    when(mockBlobContainers.list(mockAzureCloudContext.getAzureResourceGroupId(), creationParameters.getStorageAccountName()))
            .thenReturn(mockListResult);
    when(mockListResult.iterator()).thenReturn(Collections.emptyIterator());

    final StepResult stepResult = getAzureStorageContainerStep.doStep(mockFlightContext);

    // Verify step returns success
    assertThat(stepResult, equalTo(StepResult.getStepResultSuccess()));
  }

  @Test
  public void getStorageAccountContainer_storageAccountDoesNotExist() throws InterruptedException {
    final ApiAzureStorageContainerCreationParameters creationParameters =
            ControlledResourceFixtures.getAzureStorageContainerCreationParameters();

    GetAzureStorageContainerStep getAzureStorageContainerStep =
            new GetAzureStorageContainerStep(
                    mockAzureConfig,
                    mockCrlService,
                    ControlledResourceFixtures.getAzureStorageContainer(
                            creationParameters.getStorageAccountName(), creationParameters.getName()));

    when(mockNameAvailabilityResult.isAvailable()).thenReturn(true);
    when(mockStorageAccounts.checkNameAvailability(creationParameters.getStorageAccountName()))
        .thenReturn(mockNameAvailabilityResult);

    final StepResult stepResult = getAzureStorageContainerStep.doStep(mockFlightContext);

    // Verify step returns error because storage account does not exist.
    assertThat(stepResult.getStepStatus(), equalTo(StepStatus.STEP_RESULT_FAILURE_FATAL));
    assertThat(stepResult.getException().get(), instanceOf(ResourceNotFoundException.class));
  }

  @Test
  public void getStorageContainer_containerAlreadyExists() throws InterruptedException {
    final ApiAzureStorageContainerCreationParameters creationParameters =
            ControlledResourceFixtures.getAzureStorageContainerCreationParameters();

    GetAzureStorageContainerStep getAzureStorageContainerStep =
            new GetAzureStorageContainerStep(
                    mockAzureConfig,
                    mockCrlService,
                    ControlledResourceFixtures.getAzureStorageContainer(
                            creationParameters.getStorageAccountName(), creationParameters.getName()));

    // In order to be able to create a storage container, a storage account with the name must already exist
    // (so availability of the name is false because it is in use).
    when(mockNameAvailabilityResult.isAvailable()).thenReturn(false);
    when(mockStorageAccounts.checkNameAvailability(creationParameters.getStorageAccountName()))
            .thenReturn(mockNameAvailabilityResult);

    // A storage container with this name already exists.
    when(mockBlobContainers.list(mockAzureCloudContext.getAzureResourceGroupId(), creationParameters.getStorageAccountName()))
            .thenReturn(mockListResult);
    when(mockListResult.iterator()).thenReturn(Collections.singletonList(mockContainerResult).iterator());
    when(mockContainerResult.name()).thenReturn(creationParameters.getName());

    final StepResult stepResult = getAzureStorageContainerStep.doStep(mockFlightContext);

    // Verify step fails.
    assertThat(stepResult.getStepStatus(), equalTo(StepStatus.STEP_RESULT_FAILURE_FATAL));
    assertThat(stepResult.getException().get(), instanceOf(DuplicateResourceException.class));
  }
}
