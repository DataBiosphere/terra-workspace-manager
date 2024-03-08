package bio.terra.workspace.service.resource.controlled.cloud.azure.storageContainer;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import bio.terra.common.iam.BearerToken;
import bio.terra.landingzone.db.exception.LandingZoneNotFoundException;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.workspace.amalgam.landingzone.azure.LandingZoneApiDispatch;
import bio.terra.workspace.common.fixtures.ControlledAzureResourceFixtures;
import bio.terra.workspace.db.ResourceDao;
import bio.terra.workspace.generated.model.ApiAzureLandingZoneDeployedResource;
import bio.terra.workspace.generated.model.ApiAzureStorageContainerCreationParameters;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.job.JobMapKeys;
import bio.terra.workspace.service.resource.controlled.cloud.azure.storage.BaseStorageStepTest;
import bio.terra.workspace.service.workspace.WorkspaceService;
import bio.terra.workspace.service.workspace.model.Workspace;
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
  @Mock private FlightMap mockFlightMap;
  @Mock private AuthenticatedUserRequest mockAuthenticatedUserRequest;
  @Mock private SamService mockSamService;
  @Mock private WorkspaceService mockWorkspaceService;

  @Captor ArgumentCaptor<String> resourceGroupNameCaptor;
  @Captor ArgumentCaptor<String> accountNameCaptor;
  @Captor ArgumentCaptor<String> containerNameCaptor;

  private final ApiAzureStorageContainerCreationParameters creationParameters =
      ControlledAzureResourceFixtures.getAzureStorageContainerCreationParameters();
  private ControlledAzureStorageContainerResource storageContainerResource;
  private DeleteAzureStorageContainerStep deleteAzureStorageContainerStep;

  @BeforeEach
  public void setup() {
    super.setup();
    when(mockStorageManager.blobContainers()).thenReturn(mockBlobContainers);
    when(mockSamService.getWsmServiceAccountToken()).thenReturn("wsm-token");
    setupFlightContext();
  }

  private void initDeleteValidationStep(Optional<UUID> storageAccountId) {
    storageContainerResource =
        ControlledAzureResourceFixtures.getAzureStorageContainer(
            creationParameters.getStorageContainerName());

    deleteAzureStorageContainerStep =
        new DeleteAzureStorageContainerStep(
            mockAzureConfig,
            mockCrlService,
            mockLandingZoneApiDispatch,
            mockSamService,
            storageContainerResource,
            mockWorkspaceService);
  }

  private void setupFlightContext() {
    when(mockAuthenticatedUserRequest.getRequiredToken()).thenReturn("FAKE_TOKEN");
    when(mockFlightMap.get(
            eq(JobMapKeys.AUTH_USER_INFO.getKeyName()), eq(AuthenticatedUserRequest.class)))
        .thenReturn(mockAuthenticatedUserRequest);
    when(mockFlightContext.getInputParameters()).thenReturn(mockFlightMap);
  }

  @Test
  public void deleteStorageAccountContainerControlledByLzStorageAccountSuccess()
      throws InterruptedException {
    UUID landingZoneId = UUID.randomUUID();
    initDeleteValidationStep(Optional.empty());

    when(mockLandingZoneApiDispatch.getLandingZoneId(any(), any())).thenReturn(landingZoneId);
    ApiAzureLandingZoneDeployedResource mockSharedStorageAccount =
        mock(ApiAzureLandingZoneDeployedResource.class);
    String sharedAccountId = UUID.randomUUID().toString();
    when(mockSharedStorageAccount.getResourceId()).thenReturn(sharedAccountId);
    when(mockLandingZoneApiDispatch.getSharedStorageAccount(any(), eq(landingZoneId)))
        .thenReturn(Optional.of(mockSharedStorageAccount));
    String sharedStorageAccountName = "sharedStorageAccount";
    when(mockStorageAccount.name()).thenReturn(sharedStorageAccountName);
    when(mockStorageAccounts.getById(sharedAccountId)).thenReturn(mockStorageAccount);

    // act
    StepResult stepResult = deleteAzureStorageContainerStep.doStep(mockFlightContext);

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
  public void deleteStorageAccountContainerControlledByLzStorageAccountSuccess_NoLandingZone()
      throws InterruptedException {
    initDeleteValidationStep(Optional.empty());

    when(mockLandingZoneApiDispatch.getLandingZoneId(any(), any()))
        .thenThrow(
            new LandingZoneNotFoundException(
                "Could not find a landing zone id for the given Azure context. "
                    + "Please check that the landing zone deployment is complete."));

    // act
    StepResult stepResult = deleteAzureStorageContainerStep.doStep(mockFlightContext);

    assertThat(stepResult.getStepStatus(), equalTo(StepStatus.STEP_RESULT_SUCCESS));
  }

  @Test
  public void
      deleteStorageAccountContainerControlledByLzStorageAccountSuccess_NoSharedStorageAccount()
          throws InterruptedException {
    UUID landingZoneId = UUID.randomUUID();
    initDeleteValidationStep(Optional.empty());

    when(mockLandingZoneApiDispatch.getLandingZoneId(any(BearerToken.class), any(Workspace.class)))
        .thenReturn(landingZoneId);
    when(mockLandingZoneApiDispatch.getSharedStorageAccount(
            any(BearerToken.class), eq(landingZoneId)))
        .thenReturn(Optional.empty());

    // act
    StepResult stepResult = deleteAzureStorageContainerStep.doStep(mockFlightContext);

    assertThat(stepResult.getStepStatus(), equalTo(StepStatus.STEP_RESULT_SUCCESS));
  }
}
