package bio.terra.workspace.service.resource.controlled.cloud.azure.storageContainer;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.Mockito.*;

import bio.terra.common.iam.BearerToken;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.StepResult;
import bio.terra.workspace.amalgam.landingzone.azure.LandingZoneApiDispatch;
import bio.terra.workspace.app.configuration.external.AzureConfiguration;
import bio.terra.workspace.common.exception.AzureManagementExceptionUtils;
import bio.terra.workspace.common.utils.BaseMockitoStrictStubbingTest;
import bio.terra.workspace.generated.model.ApiAzureLandingZoneDeployedResource;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.workspace.WorkspaceService;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import bio.terra.workspace.service.workspace.model.AzureCloudContext;
import bio.terra.workspace.service.workspace.model.Workspace;
import com.azure.core.http.HttpResponse;
import com.azure.core.management.exception.ManagementError;
import com.azure.core.management.exception.ManagementException;
import com.azure.resourcemanager.storage.StorageManager;
import com.azure.resourcemanager.storage.models.BlobContainers;
import com.azure.resourcemanager.storage.models.StorageAccount;
import com.azure.resourcemanager.storage.models.StorageAccounts;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;

@Tag("unit")
public class DeleteAzureStorageContainerStepUnitTest extends BaseMockitoStrictStubbingTest {

  @Mock AzureConfiguration azureConfig;
  @Mock CrlService crlService;
  @Mock LandingZoneApiDispatch landingZoneApiDispatch;
  @Mock SamService samService;
  @Mock ControlledAzureStorageContainerResource resource;
  @Mock WorkspaceService workspaceService;

  @Mock StorageManager storageManager;
  @Mock StorageAccounts storageAccounts;
  @Mock BlobContainers blobContainers;

  @Mock AzureCloudContext azureCloudContext;
  @Mock Workspace workspace;
  @Mock FlightMap workingMap;
  @Mock FlightContext context;

  @BeforeEach
  void localSetup() {
    Mockito.lenient().when(context.getWorkingMap()).thenReturn(workingMap);
    Mockito.lenient()
        .when(
            workingMap.get(
                WorkspaceFlightMapKeys.ControlledResourceKeys.AZURE_CLOUD_CONTEXT,
                AzureCloudContext.class))
        .thenReturn(azureCloudContext);
    Mockito.lenient()
        .when(crlService.getStorageManager(azureCloudContext, azureConfig))
        .thenReturn(storageManager);
    Mockito.lenient().when(storageManager.storageAccounts()).thenReturn(storageAccounts);
    Mockito.lenient().when(storageManager.blobContainers()).thenReturn(blobContainers);
  }

  @Test
  void happyPathDeletingStorageContainer() throws Exception {
    var token = new BearerToken("test-token");
    when(samService.getWsmServiceAccountToken()).thenReturn(token.getToken());

    var workspaceId = UUID.randomUUID();
    when(resource.getWorkspaceId()).thenReturn(workspaceId);
    when(workspaceService.getWorkspace(workspaceId)).thenReturn(workspace);

    var landingZoneId = UUID.randomUUID();
    when(landingZoneApiDispatch.getLandingZoneId(token, workspace)).thenReturn(landingZoneId);
    var sharedStorageAccount = mock(ApiAzureLandingZoneDeployedResource.class);
    when(landingZoneApiDispatch.getSharedStorageAccount(token, landingZoneId))
        .thenReturn(Optional.of(sharedStorageAccount));

    var sharedStorageAccountId = "sharedStorageAccount-resource-id";
    when(sharedStorageAccount.getResourceId()).thenReturn(sharedStorageAccountId);
    var storageAccount = mock(StorageAccount.class);
    when(storageAccounts.getById(sharedStorageAccountId)).thenReturn(storageAccount);
    var storageAccountName = "storage-account-name";
    when(storageAccount.name()).thenReturn(storageAccountName);

    var azureResourceGroupId = "test-azure-resourceGroupId";
    when(azureCloudContext.getAzureResourceGroupId()).thenReturn(azureResourceGroupId);
    var storageContainerResourceName = "sharedStorageAccount-resource-name";
    when(resource.getStorageContainerName()).thenReturn(storageContainerResourceName);

    doNothing()
        .when(blobContainers)
        .delete(azureResourceGroupId, storageAccountName, storageContainerResourceName);

    var step =
        new DeleteAzureStorageContainerStep(
            azureConfig,
            crlService,
            landingZoneApiDispatch,
            samService,
            resource,
            workspaceService);

    assertThat(step.doStep(context), equalTo(StepResult.getStepResultSuccess()));
    verify(blobContainers)
        .delete(azureResourceGroupId, storageAccountName, storageContainerResourceName);
  }

  @Test
  void stepSucceedsWhenResourceIsAlreadyGone() throws Exception {
    var token = new BearerToken("test-token");
    when(samService.getWsmServiceAccountToken()).thenReturn(token.getToken());

    var workspaceId = UUID.randomUUID();
    when(resource.getWorkspaceId()).thenReturn(workspaceId);
    when(workspaceService.getWorkspace(workspaceId)).thenReturn(workspace);

    var landingZoneId = UUID.randomUUID();
    when(landingZoneApiDispatch.getLandingZoneId(token, workspace)).thenReturn(landingZoneId);
    var sharedStorageAccount = mock(ApiAzureLandingZoneDeployedResource.class);
    when(landingZoneApiDispatch.getSharedStorageAccount(token, landingZoneId))
        .thenReturn(Optional.of(sharedStorageAccount));

    var sharedStorageAccountId = "sharedStorageAccount-resource-id";
    when(sharedStorageAccount.getResourceId()).thenReturn(sharedStorageAccountId);
    var storageAccount = mock(StorageAccount.class);
    when(storageAccounts.getById(sharedStorageAccountId)).thenReturn(storageAccount);
    var storageAccountName = "storage-account-name";
    when(storageAccount.name()).thenReturn(storageAccountName);

    var azureResourceGroupId = "test-azure-resourceGroupId";
    when(azureCloudContext.getAzureResourceGroupId()).thenReturn(azureResourceGroupId);
    var storageContainerResourceName = "sharedStorageAccount-resource-name";
    when(resource.getStorageContainerName()).thenReturn(storageContainerResourceName);

    var response = mock(HttpResponse.class);
    when(response.getStatusCode()).thenReturn(404);
    var error = new ManagementError("NotFound", AzureManagementExceptionUtils.RESOURCE_NOT_FOUND);
    var exception =
        new ManagementException(AzureManagementExceptionUtils.RESOURCE_NOT_FOUND, response, error);

    doThrow(exception)
        .when(blobContainers)
        .delete(azureResourceGroupId, storageAccountName, storageContainerResourceName);

    var step =
        new DeleteAzureStorageContainerStep(
            azureConfig,
            crlService,
            landingZoneApiDispatch,
            samService,
            resource,
            workspaceService);
    assertThat(step.doStep(context), equalTo(StepResult.getStepResultSuccess()));
  }
}
