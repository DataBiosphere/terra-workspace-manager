package bio.terra.workspace.service.resource.controlled.cloud.azure.storageContainer;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.mockito.Mockito.when;

import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.workspace.amalgam.landingzone.azure.LandingZoneApiDispatch;
import bio.terra.workspace.common.fixtures.ControlledResourceFixtures;
import bio.terra.workspace.common.utils.ManagementExceptionUtils;
import bio.terra.workspace.db.ResourceDao;
import bio.terra.workspace.generated.model.ApiAzureStorageContainerCreationParameters;
import bio.terra.workspace.service.resource.controlled.cloud.azure.storage.BaseStorageStepTest;
import bio.terra.workspace.service.resource.controlled.cloud.azure.storage.ControlledAzureStorageResource;
import bio.terra.workspace.service.resource.exception.DuplicateResourceException;
import bio.terra.workspace.service.resource.exception.ResourceNotFoundException;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import com.azure.core.management.exception.ManagementError;
import com.azure.core.management.exception.ManagementException;
import com.azure.resourcemanager.storage.models.BlobContainer;
import com.azure.resourcemanager.storage.models.BlobContainers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

public class VerifyAzureStorageContainerCanBeCreatedStepTest extends BaseStorageStepTest {

  @Mock private BlobContainers mockBlobContainers;
  @Mock private BlobContainer mockBlobContainer;
  @Mock private ResourceDao mockResourceDao;
  @Mock private LandingZoneApiDispatch mockLandingZoneApiDispatch;

  private final String storageAccountName = ControlledResourceFixtures.uniqueStorageAccountName();
  final ApiAzureStorageContainerCreationParameters creationParameters =
      ControlledResourceFixtures.getAzureStorageContainerCreationParameters();
  final ControlledAzureStorageContainerResource storageContainerResource =
      ControlledResourceFixtures.getAzureStorageContainer(
          creationParameters.getStorageAccountId(), creationParameters.getStorageContainerName());
  private final ControlledAzureStorageResource storageAccountResource =
      ControlledResourceFixtures.getAzureStorage(storageAccountName, "mockRegion");
  private final ManagementException containerNotFoundException =
      new ManagementException(
          "Resource was not found.",
          /*response=*/ null,
          new ManagementError(
              ManagementExceptionUtils.CONTAINER_NOT_FOUND, "Container was not found."));

  private VerifyAzureStorageContainerCanBeCreatedStep verifyCanBeCreatedStep;

  @BeforeEach
  public void setup() {
    super.setup();
    when(mockStorageManager.blobContainers()).thenReturn(mockBlobContainers);
    verifyCanBeCreatedStep =
        new VerifyAzureStorageContainerCanBeCreatedStep(
            mockAzureConfig, mockCrlService, mockResourceDao, mockLandingZoneApiDispatch, storageContainerResource);
  }

  private void mockStorageAccountExists() {
    when(mockResourceDao.getResource(
            storageContainerResource.getWorkspaceId(), creationParameters.getStorageAccountId()))
        .thenReturn(storageAccountResource);
    when(mockStorageAccounts.getByResourceGroup(
            mockAzureCloudContext.getAzureResourceGroupId(), storageAccountName))
        .thenReturn(mockStorageAccount);
    when(mockFlightContext
            .getWorkingMap()
            .get(WorkspaceFlightMapKeys.ControlledResourceKeys.STORAGE_ACCOUNT_NAME, String.class))
        .thenReturn(storageAccountName);
  }

  @Test
  public void getStorageContainer_containerCanBeCreated() throws InterruptedException {
    mockStorageAccountExists();

    // The storage container must not already exist.
    when(mockBlobContainers.get(
            mockAzureCloudContext.getAzureResourceGroupId(),
            storageAccountName,
            creationParameters.getStorageContainerName()))
        .thenThrow(containerNotFoundException);

    final StepResult stepResult = verifyCanBeCreatedStep.doStep(mockFlightContext);

    // Verify step returns success
    assertThat(stepResult, equalTo(StepResult.getStepResultSuccess()));
  }

  @Test
  public void getStorageAccountContainer_storageAccountDoesNotExistInWSM()
      throws InterruptedException {
    // Storage account doesn't exist in WSM
    when(mockResourceDao.getResource(
            storageContainerResource.getWorkspaceId(), creationParameters.getStorageAccountId()))
        .thenThrow(new ResourceNotFoundException("Not Found"));

    final StepResult stepResult = verifyCanBeCreatedStep.doStep(mockFlightContext);

    // Verify step returns error because storage account does not exist.
    assertThat(stepResult.getStepStatus(), equalTo(StepStatus.STEP_RESULT_FAILURE_FATAL));
    assertThat(stepResult.getException().get(), instanceOf(ResourceNotFoundException.class));
  }

  @Test
  public void getStorageAccountContainer_storageAccountDoesNotExistInAzure()
      throws InterruptedException {
    // Storage account exists in WSM.
    when(mockResourceDao.getResource(
            storageContainerResource.getWorkspaceId(), creationParameters.getStorageAccountId()))
        .thenReturn(storageAccountResource);

    // Storage account doesn't exist in Azure
    when(mockStorageAccounts.getByResourceGroup(
            mockAzureCloudContext.getAzureResourceGroupId(), storageAccountName))
        .thenThrow(resourceNotFoundException);

    final StepResult stepResult = verifyCanBeCreatedStep.doStep(mockFlightContext);

    // Verify step returns error because storage account does not exist.
    assertThat(stepResult.getStepStatus(), equalTo(StepStatus.STEP_RESULT_FAILURE_FATAL));
    assertThat(stepResult.getException().get(), instanceOf(ResourceNotFoundException.class));
  }

  @Test
  public void getStorageContainer_containerAlreadyExists() throws InterruptedException {
    mockStorageAccountExists();

    // A storage container with this name already exists.
    when(mockBlobContainers.get(
            mockAzureCloudContext.getAzureResourceGroupId(),
            storageAccountName,
            creationParameters.getStorageContainerName()))
        .thenReturn(mockBlobContainer);

    final StepResult stepResult = verifyCanBeCreatedStep.doStep(mockFlightContext);

    // Verify step fails.
    assertThat(stepResult.getStepStatus(), equalTo(StepStatus.STEP_RESULT_FAILURE_FATAL));
    assertThat(stepResult.getException().get(), instanceOf(DuplicateResourceException.class));
  }
}
