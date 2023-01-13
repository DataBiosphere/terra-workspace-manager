package bio.terra.workspace.common;

import bio.terra.workspace.common.utils.AzureTestUtils;
import bio.terra.workspace.connected.AzureConnectedTestUtils;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.spendprofile.SpendProfile;
import bio.terra.workspace.service.spendprofile.SpendProfileId;
import bio.terra.workspace.service.spendprofile.SpendProfileService;
import bio.terra.workspace.service.workspace.WorkspaceService;
import bio.terra.workspace.service.workspace.model.CloudPlatform;
import bio.terra.workspace.service.workspace.model.Workspace;
import java.util.UUID;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

/** Base class for azure tests. Treat these as connected tests: connected to Azure */
@ActiveProfiles({"azure-test", "connected-test"})
public class BaseAzureConnectedTest extends BaseTest {

  @MockBean private SpendProfileService mockSpendProfileService;

  @Autowired protected AzureTestUtils azureTestUtils;
  @Autowired protected AzureConnectedTestUtils azureUtils;

  public SpendProfileService mockSpendProfileService() {
    return mockSpendProfileService;
  }

  protected Workspace createWorkspaceWithCloudContext(
      WorkspaceService workspaceService, AuthenticatedUserRequest userRequest)
      throws InterruptedException {
    initSpendProfileMock();
    Workspace workspace = azureTestUtils.createWorkspace(workspaceService);
    azureUtils.createCloudContext(workspace.getWorkspaceId(), userRequest);
    return workspace;
  }

  protected SpendProfileId initSpendProfileMock() {
    Mockito.when(
            mockSpendProfileService()
                .authorizeLinking(
                    Mockito.eq(azureTestUtils.getSpendProfileId()),
                    Mockito.eq(true),
                    Mockito.any()))
        .thenReturn(
            new SpendProfile(
                azureTestUtils.getSpendProfileId(),
                CloudPlatform.AZURE,
                null,
                UUID.fromString(azureTestUtils.getAzureCloudContext().getAzureTenantId()),
                UUID.fromString(azureTestUtils.getAzureCloudContext().getAzureSubscriptionId()),
                azureTestUtils.getAzureCloudContext().getAzureResourceGroupId()));

    return azureTestUtils.getSpendProfileId();
  }
}
