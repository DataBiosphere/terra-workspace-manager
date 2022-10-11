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
import bio.terra.landingzone.library.landingzones.deployment.ResourcePurpose;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.workspace.amalgam.landingzone.azure.LandingZoneApiDispatch;
import bio.terra.workspace.common.fixtures.ControlledResourceFixtures;
import bio.terra.workspace.common.utils.ManagementExceptionUtils;
import bio.terra.workspace.db.ResourceDao;
import bio.terra.workspace.generated.model.ApiAzureLandingZoneDeployedResource;
import bio.terra.workspace.generated.model.ApiAzureLandingZoneResourcesList;
import bio.terra.workspace.generated.model.ApiAzureLandingZoneResourcesPurposeGroup;
import bio.terra.workspace.generated.model.ApiAzureStorageContainerCreationParameters;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.resource.controlled.cloud.azure.storage.BaseStorageStepTest;
import bio.terra.workspace.service.resource.controlled.cloud.azure.storage.ControlledAzureStorageResource;
import bio.terra.workspace.service.resource.exception.DuplicateResourceException;
import bio.terra.workspace.service.resource.exception.ResourceNotFoundException;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import com.azure.core.management.exception.ManagementError;
import com.azure.core.management.exception.ManagementException;
import com.azure.resourcemanager.storage.models.BlobContainer;
import com.azure.resourcemanager.storage.models.BlobContainers;
import java.util.Collections;
import java.util.List;
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

  private static final UUID LANDING_ZONE_ID =
      UUID.fromString("b2db9b47-fd0f-4ae9-b9b4-f675550b0291");

  private final String storageAccountName = ControlledResourceFixtures.uniqueStorageAccountName();
  final ApiAzureStorageContainerCreationParameters creationParameters =
      ControlledResourceFixtures.getAzureStorageContainerCreationParameters();
  private ControlledAzureStorageContainerResource storageContainerResource;
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
  }

  private void initValidationStep(Optional<UUID> storageAccountId) {
    storageContainerResource =
        ControlledResourceFixtures.getAzureStorageContainer(
            storageAccountId.orElse(null), creationParameters.getStorageContainerName());

    verifyCanBeCreatedStep =
        new VerifyAzureStorageContainerCanBeCreatedStep(
            mockAzureConfig,
            mockCrlService,
            mockResourceDao,
            mockLandingZoneApiDispatch,
            mockUserRequest,
            storageContainerResource);
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
    initValidationStep(Optional.of(creationParameters.getStorageAccountId()));
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
  public void getStorageContainer_containerCanBeCreatedBasedOnLandingZoneSharedStorageAccount()
      throws InterruptedException {
    initValidationStep(Optional.empty());

    when(mockLandingZoneApiDispatch.getLandingZoneId(any())).thenReturn(LANDING_ZONE_ID);
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

    // act
    final StepResult stepResult = verifyCanBeCreatedStep.doStep(mockFlightContext);

    assertThat(stepResult, equalTo(StepResult.getStepResultSuccess()));
  }

  @Test
  public void getStorageAccountContainer_storageAccountDoesNotExistInWSM()
      throws InterruptedException {
    initValidationStep(Optional.of(creationParameters.getStorageAccountId()));
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
    initValidationStep(Optional.of(creationParameters.getStorageAccountId()));
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
  public void getStorageAccountContainer_landingZoneDoesntExist() throws InterruptedException {
    initValidationStep(Optional.empty());

    // there are no landing zone association with azure cloud context
    when(mockLandingZoneApiDispatch.getLandingZoneId(any()))
        .thenThrow(
            new IllegalStateException(
                "Could not find a landing zone id for the given Azure context. "
                    + "Please check that the landing zone deployment is complete."));

    // act
    final StepResult stepResult = verifyCanBeCreatedStep.doStep(mockFlightContext);

    assertThat(stepResult.getStepStatus(), equalTo(StepStatus.STEP_RESULT_FAILURE_FATAL));
    assertThat(stepResult.getException().get(), instanceOf(LandingZoneNotFoundException.class));
  }

  @Test
  public void getStorageAccountContainer_landingZoneDoesntHaveSharedStorageAccount()
      throws InterruptedException {
    initValidationStep(Optional.empty());

    when(mockLandingZoneApiDispatch.getLandingZoneId(any())).thenReturn(LANDING_ZONE_ID);
    when(mockLandingZoneApiDispatch.getSharedStorageAccount(
            any(BearerToken.class), eq(LANDING_ZONE_ID)))
        .thenReturn(Optional.empty());
    when(mockUserRequest.getRequiredToken()).thenReturn("FAKE_TOKEN");

    // act
    final StepResult stepResult = verifyCanBeCreatedStep.doStep(mockFlightContext);

    assertThat(stepResult.getStepStatus(), equalTo(StepStatus.STEP_RESULT_FAILURE_FATAL));
    assertThat(stepResult.getException().get(), instanceOf(ResourceNotFoundException.class));
  }

  @Test
  public void getStorageContainer_containerAlreadyExists() throws InterruptedException {
    initValidationStep(Optional.of(creationParameters.getStorageAccountId()));
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

  private ApiAzureLandingZoneResourcesList getExistingLandingZoneResources() {
    List<ApiAzureLandingZoneDeployedResource> landingZoneDeployedResourcesWithSharedPurpose =
        Collections.singletonList(
            new ApiAzureLandingZoneDeployedResource()
                .resourceId("resourceId")
                .resourceType(
                    VerifyAzureStorageContainerCanBeCreatedStep.AZURE_STORAGE_ACCOUNT_RESOURCE_TYPE)
                .region("us-west-2")
                .resourceName("sharedStorageAccount"));
    ApiAzureLandingZoneResourcesPurposeGroup landingZoneSharedResourcesPurposeGroup =
        new ApiAzureLandingZoneResourcesPurposeGroup()
            .purpose(ResourcePurpose.SHARED_RESOURCE.toString())
            .deployedResources(landingZoneDeployedResourcesWithSharedPurpose);

    List<ApiAzureLandingZoneDeployedResource> landingZoneDeployedResourcesWithDifferentPurpose =
        Collections.singletonList(
            new ApiAzureLandingZoneDeployedResource()
                .resourceId("resourceId")
                .resourceType("Microsoft.Storage/otherResource")
                .region("us-west-2")
                .resourceName("otherResourceName"));
    ApiAzureLandingZoneResourcesPurposeGroup landingZoneOtherPurposeResourcesPurposeGroup =
        new ApiAzureLandingZoneResourcesPurposeGroup()
            .purpose("OTHER_PURPOSE")
            .deployedResources(landingZoneDeployedResourcesWithDifferentPurpose);

    List<ApiAzureLandingZoneResourcesPurposeGroup> resourceGroupList =
        List.of(
            landingZoneSharedResourcesPurposeGroup, landingZoneOtherPurposeResourcesPurposeGroup);

    return new ApiAzureLandingZoneResourcesList().id(LANDING_ZONE_ID).resources(resourceGroupList);
  }

  private ApiAzureLandingZoneResourcesList getLandingZoneWithoutSharedStorageAccount() {
    List<ApiAzureLandingZoneDeployedResource> landingZoneDeployedResourcesWithDifferentPurpose =
        Collections.singletonList(
            new ApiAzureLandingZoneDeployedResource()
                .resourceId("resourceId")
                .resourceType("Microsoft.Storage/otherResource")
                .region("us-west-2")
                .resourceName("otherResourceName"));
    ApiAzureLandingZoneResourcesPurposeGroup landingZoneOtherPurposeResourcesPurposeGroup =
        new ApiAzureLandingZoneResourcesPurposeGroup()
            .purpose("OTHER_PURPOSE")
            .deployedResources(landingZoneDeployedResourcesWithDifferentPurpose);

    List<ApiAzureLandingZoneResourcesPurposeGroup> resourceGroupList =
        List.of(landingZoneOtherPurposeResourcesPurposeGroup);

    return new ApiAzureLandingZoneResourcesList().id(LANDING_ZONE_ID).resources(resourceGroupList);
  }
}
