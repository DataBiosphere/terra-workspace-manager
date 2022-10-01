package bio.terra.workspace.service.resource.controlled.cloud.azure.storageContainer;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import bio.terra.landingzone.db.exception.LandingZoneNotFoundException;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.workspace.amalgam.landingzone.azure.LandingZoneApiDispatch;
import bio.terra.workspace.common.fixtures.ControlledResourceFixtures;
import bio.terra.workspace.db.ResourceDao;
import bio.terra.workspace.generated.model.ApiAzureLandingZoneDeployedResource;
import bio.terra.workspace.generated.model.ApiAzureStorageContainerCreationParameters;
import bio.terra.workspace.service.resource.controlled.cloud.azure.storage.BaseStorageStepTest;
import bio.terra.workspace.service.resource.controlled.cloud.azure.storage.ControlledAzureStorageResource;
import bio.terra.workspace.service.resource.exception.ResourceNotFoundException;
import bio.terra.workspace.service.workspace.model.AzureCloudContext;
import com.azure.resourcemanager.storage.models.BlobContainers;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;

public class DeleteAzureStorageContainerStepTest extends BaseStorageStepTest {
  @Mock private BlobContainers mockBlobContainers;
  @Mock private ResourceDao mockResourceDao;
  @Mock private LandingZoneApiDispatch mockLandingZoneApiDispatch;

  @Captor ArgumentCaptor<String> resourceGroupNameCaptor;
  @Captor ArgumentCaptor<String> accountNameCaptor;
  @Captor ArgumentCaptor<String> containerNameCaptor;

  private final ApiAzureStorageContainerCreationParameters creationParameters =
      ControlledResourceFixtures.getAzureStorageContainerCreationParameters();
  private final String storageAccountName = ControlledResourceFixtures.uniqueStorageAccountName();
  private final ControlledAzureStorageResource storageAccountResource =
      ControlledResourceFixtures.getAzureStorage(storageAccountName, "mockRegion");
  private ControlledAzureStorageContainerResource storageContainerResource;
  private DeleteAzureStorageContainerStep deleteAzureStorageContainerStep;

  @BeforeEach
  public void setup() {
    super.setup();
    when(mockStorageManager.blobContainers()).thenReturn(mockBlobContainers);
  }

  private void initDeleteValidationStep(Optional<UUID> storageAccountId) {
    storageContainerResource =
        ControlledResourceFixtures.getAzureStorageContainer(
            storageAccountId.orElse(null), creationParameters.getStorageContainerName());

    deleteAzureStorageContainerStep =
        new DeleteAzureStorageContainerStep(
            mockAzureConfig,
            mockCrlService,
            mockResourceDao,
            mockLandingZoneApiDispatch,
            storageContainerResource);
  }

  @Test
  public void deleteStorageAccountContainerControlledByWsmStorageAccountSuccess()
      throws InterruptedException {
    initDeleteValidationStep(Optional.of(creationParameters.getStorageAccountId()));
    when(mockResourceDao.getResource(
            storageContainerResource.getWorkspaceId(), creationParameters.getStorageAccountId()))
        .thenReturn(storageAccountResource);

    // act
    final StepResult stepResult = deleteAzureStorageContainerStep.doStep(mockFlightContext);

    assertThat(stepResult, equalTo(StepResult.getStepResultSuccess()));

    verify(mockBlobContainers, times(1))
        .delete(
            resourceGroupNameCaptor.capture(),
            accountNameCaptor.capture(),
            containerNameCaptor.capture());
    assertThat(
        resourceGroupNameCaptor.getValue(),
        equalTo(mockAzureCloudContext.getAzureResourceGroupId()));
    assertThat(
        accountNameCaptor.getValue(), equalTo(storageAccountResource.getStorageAccountName()));
    assertThat(
        containerNameCaptor.getValue(),
        equalTo(storageContainerResource.getStorageContainerName()));
  }

  @Test
  public void deleteStorageAccountContainerControlledByLzStorageAccountSuccess()
      throws InterruptedException {
    UUID landingZoneId = UUID.randomUUID();
    initDeleteValidationStep(Optional.empty());

    when(mockLandingZoneApiDispatch.getLandingZoneId(any(AzureCloudContext.class)))
        .thenReturn(landingZoneId.toString());
    ApiAzureLandingZoneDeployedResource mockSharedStorageAccount =
        mock(ApiAzureLandingZoneDeployedResource.class);
    String sharedAccountId = UUID.randomUUID().toString();
    when(mockSharedStorageAccount.getResourceId()).thenReturn(sharedAccountId);
    when(mockLandingZoneApiDispatch.getSharedStorageAccount(landingZoneId.toString()))
        .thenReturn(Optional.of(mockSharedStorageAccount));
    String sharedStorageAccountName = "sharedStorageAccount";
    when(mockStorageAccount.name()).thenReturn(sharedStorageAccountName);
    when(mockStorageAccounts.getById(sharedAccountId)).thenReturn(mockStorageAccount);

    // act
    final StepResult stepResult = deleteAzureStorageContainerStep.doStep(mockFlightContext);

    assertThat(stepResult, equalTo(StepResult.getStepResultSuccess()));

    verify(mockBlobContainers, times(1))
        .delete(
            resourceGroupNameCaptor.capture(),
            accountNameCaptor.capture(),
            containerNameCaptor.capture());
    assertThat(
        resourceGroupNameCaptor.getValue(),
        equalTo(mockAzureCloudContext.getAzureResourceGroupId()));
    assertThat(accountNameCaptor.getValue(), equalTo(sharedStorageAccountName));
    assertThat(
        containerNameCaptor.getValue(),
        equalTo(storageContainerResource.getStorageContainerName()));
  }

  @Test
  public void deleteStorageAccountContainerControlledByLzStorageAccountFailure_NoLandingZone()
      throws InterruptedException {
    initDeleteValidationStep(Optional.empty());

    when(mockLandingZoneApiDispatch.getLandingZoneId(any(AzureCloudContext.class)))
        .thenThrow(
            new IllegalStateException(
                "Could not find a landing zone id for the given Azure context. "
                    + "Please check that the landing zone deployment is complete."));

    // act
    final StepResult stepResult = deleteAzureStorageContainerStep.doStep(mockFlightContext);

    assertThat(stepResult.getStepStatus(), equalTo(StepStatus.STEP_RESULT_FAILURE_FATAL));
    assertThat(stepResult.getException().get(), instanceOf(LandingZoneNotFoundException.class));
  }

  @Test
  public void
      deleteStorageAccountContainerControlledByLzStorageAccountFailure_NoSharedStorageAccount()
          throws InterruptedException {
    UUID landingZoneId = UUID.randomUUID();
    initDeleteValidationStep(Optional.empty());

    when(mockLandingZoneApiDispatch.getLandingZoneId(any(AzureCloudContext.class)))
        .thenReturn(landingZoneId.toString());
    when(mockLandingZoneApiDispatch.getSharedStorageAccount(landingZoneId.toString()))
        .thenReturn(Optional.empty());

    // act
    final StepResult stepResult = deleteAzureStorageContainerStep.doStep(mockFlightContext);

    assertThat(stepResult.getStepStatus(), equalTo(StepStatus.STEP_RESULT_FAILURE_FATAL));
    assertThat(stepResult.getException().get(), instanceOf(ResourceNotFoundException.class));
  }
}
