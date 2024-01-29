package bio.terra.workspace.service.resource.controlled.cloud.azure.storageContainer;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import bio.terra.common.iam.BearerToken;
import bio.terra.landingzone.db.exception.LandingZoneNotFoundException;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.workspace.amalgam.landingzone.azure.LandingZoneApiDispatch;
import bio.terra.workspace.common.exception.AzureManagementExceptionUtils;
import bio.terra.workspace.common.fixtures.ControlledAzureResourceFixtures;
import bio.terra.workspace.db.ResourceDao;
import bio.terra.workspace.generated.model.ApiAzureLandingZoneDeployedResource;
import bio.terra.workspace.generated.model.ApiAzureStorageContainerCreationParameters;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.resource.controlled.cloud.azure.storage.BaseStorageStepTest;
import bio.terra.workspace.service.resource.exception.DuplicateResourceException;
import bio.terra.workspace.service.resource.exception.ResourceNotFoundException;
import bio.terra.workspace.service.workspace.WorkspaceService;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import com.azure.core.management.exception.ManagementError;
import com.azure.core.management.exception.ManagementException;
import com.azure.resourcemanager.storage.models.BlobContainer;
import com.azure.resourcemanager.storage.models.BlobContainers;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

public class VerifyAzureStorageContainerCanBeCreatedStepTest extends BaseStorageStepTest {

  @Mock private BlobContainers mockBlobContainers;
  @Mock private BlobContainer mockBlobContainer;
  @Mock private ResourceDao mockResourceDao;
  @Mock private LandingZoneApiDispatch mockLandingZoneApiDispatch;
  @Mock private AuthenticatedUserRequest mockUserRequest;
  @Mock private SamService mockSamSerivce;
  @Mock private WorkspaceService mockWorkspaceService;

  private static final UUID LANDING_ZONE_ID =
      UUID.fromString("b2db9b47-fd0f-4ae9-b9b4-f675550b0291");
  private final ApiAzureStorageContainerCreationParameters creationParameters =
      ControlledAzureResourceFixtures.getAzureStorageContainerCreationParameters();
  private ControlledAzureStorageContainerResource storageContainerResource;
  private final ManagementException containerNotFoundException =
      new ManagementException(
          "Resource was not found.",
          /* response= */ null,
          new ManagementError(
              AzureManagementExceptionUtils.CONTAINER_NOT_FOUND, "Container was not found."));

  private VerifyAzureStorageContainerCanBeCreatedStep verifyCanBeCreatedStep;

  @BeforeEach
  public void setup() {
    super.setup();
    when(mockStorageManager.blobContainers()).thenReturn(mockBlobContainers);
    when(mockSamSerivce.getWsmServiceAccountToken()).thenReturn("wsm-token");
  }

  private void initValidationStep() {
    storageContainerResource =
        ControlledAzureResourceFixtures.getAzureStorageContainer(
            creationParameters.getStorageContainerName());

    verifyCanBeCreatedStep =
        new VerifyAzureStorageContainerCanBeCreatedStep(
            mockAzureConfig,
            mockCrlService,
            mockResourceDao,
            mockLandingZoneApiDispatch,
            mockSamSerivce,
            storageContainerResource,
            mockWorkspaceService);
  }

  @Test
  public void getStorageContainer_containerCanBeCreatedBasedOnLandingZoneSharedStorageAccount()
      throws InterruptedException {
    initValidationStep();

    when(mockLandingZoneApiDispatch.getLandingZoneId(any(BearerToken.class), any()))
        .thenReturn(LANDING_ZONE_ID);
    ApiAzureLandingZoneDeployedResource mockSharedStorageAccount =
        mock(ApiAzureLandingZoneDeployedResource.class);
    when(mockLandingZoneApiDispatch.getSharedStorageAccount(
            any(BearerToken.class), eq(LANDING_ZONE_ID)))
        .thenReturn(Optional.of(mockSharedStorageAccount));
    String sharedAccountId = UUID.randomUUID().toString();
    when(mockSharedStorageAccount.getResourceId()).thenReturn(sharedAccountId);
    String sharedStorageAccountName = "sharedStorageAccount";
    when(mockStorageAccount.name()).thenReturn(sharedStorageAccountName);
    when(mockStorageAccounts.getById(sharedAccountId)).thenReturn(mockStorageAccount);
    when(mockUserRequest.getRequiredToken()).thenReturn("FAKE_TOKEN");
    when(mockFlightContext
            .getWorkingMap()
            .get(WorkspaceFlightMapKeys.ControlledResourceKeys.STORAGE_ACCOUNT_NAME, String.class))
        .thenReturn(sharedStorageAccountName);
    when(mockBlobContainers.get(
            mockAzureCloudContext.getAzureResourceGroupId(),
            sharedStorageAccountName,
            creationParameters.getStorageContainerName()))
        .thenThrow(containerNotFoundException);

    // act
    StepResult stepResult = verifyCanBeCreatedStep.doStep(mockFlightContext);

    assertThat(stepResult, equalTo(StepResult.getStepResultSuccess()));
  }

  @Test
  public void getStorageAccountContainer_landingZoneDoesntExist() throws InterruptedException {
    initValidationStep();

    when(mockUserRequest.getRequiredToken()).thenReturn("FAKE_TOKEN");
    // there are no landing zone association with azure cloud context
    when(mockLandingZoneApiDispatch.getLandingZoneId(any(), any()))
        .thenThrow(
            new LandingZoneNotFoundException(
                "Could not find a landing zone id for the given Azure context. "
                    + "Please check that the landing zone deployment is complete."));

    // act
    StepResult stepResult = verifyCanBeCreatedStep.doStep(mockFlightContext);

    assertThat(stepResult.getStepStatus(), equalTo(StepStatus.STEP_RESULT_FAILURE_FATAL));
    assertThat(stepResult.getException().get(), instanceOf(LandingZoneNotFoundException.class));
  }

  @Test
  public void getStorageAccountContainer_landingZoneDoesntHaveSharedStorageAccount()
      throws InterruptedException {
    initValidationStep();

    when(mockLandingZoneApiDispatch.getLandingZoneId(any(BearerToken.class), any()))
        .thenReturn(LANDING_ZONE_ID);
    when(mockLandingZoneApiDispatch.getSharedStorageAccount(
            any(BearerToken.class), eq(LANDING_ZONE_ID)))
        .thenReturn(Optional.empty());
    when(mockUserRequest.getRequiredToken()).thenReturn("FAKE_TOKEN");

    // act
    StepResult stepResult = verifyCanBeCreatedStep.doStep(mockFlightContext);

    assertThat(stepResult.getStepStatus(), equalTo(StepStatus.STEP_RESULT_FAILURE_FATAL));
    assertThat(stepResult.getException().get(), instanceOf(ResourceNotFoundException.class));
  }

  @Test
  public void getStorageContainer_containerAlreadyExistsInLandingZoneSharedStorageAccount()
      throws InterruptedException {
    initValidationStep();

    when(mockLandingZoneApiDispatch.getLandingZoneId(any(BearerToken.class), any()))
        .thenReturn(LANDING_ZONE_ID);
    ApiAzureLandingZoneDeployedResource mockSharedStorageAccount =
        mock(ApiAzureLandingZoneDeployedResource.class);
    when(mockLandingZoneApiDispatch.getSharedStorageAccount(
            any(BearerToken.class), eq(LANDING_ZONE_ID)))
        .thenReturn(Optional.of(mockSharedStorageAccount));
    String sharedAccountId = UUID.randomUUID().toString();
    when(mockSharedStorageAccount.getResourceId()).thenReturn(sharedAccountId);
    String sharedStorageAccountName = "sharedStorageAccount";
    when(mockStorageAccount.name()).thenReturn(sharedStorageAccountName);
    when(mockStorageAccounts.getById(sharedAccountId)).thenReturn(mockStorageAccount);
    when(mockUserRequest.getRequiredToken()).thenReturn("FAKE_TOKEN");
    when(mockFlightContext
            .getWorkingMap()
            .get(WorkspaceFlightMapKeys.ControlledResourceKeys.STORAGE_ACCOUNT_NAME, String.class))
        .thenReturn(sharedStorageAccountName);
    when(mockBlobContainers.get(
            mockAzureCloudContext.getAzureResourceGroupId(),
            sharedStorageAccountName,
            creationParameters.getStorageContainerName()))
        .thenReturn(mockBlobContainer);

    // act
    StepResult stepResult = verifyCanBeCreatedStep.doStep(mockFlightContext);

    assertThat(stepResult.getStepStatus(), equalTo(StepStatus.STEP_RESULT_FAILURE_FATAL));
    assertThat(stepResult.getException().get(), instanceOf(DuplicateResourceException.class));
  }
}
