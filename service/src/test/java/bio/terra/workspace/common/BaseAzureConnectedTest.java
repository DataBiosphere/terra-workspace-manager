package bio.terra.workspace.common;

import bio.terra.landingzone.db.LandingZoneDao;
import bio.terra.workspace.common.utils.AzureTestUtils;
import bio.terra.workspace.connected.AzureConnectedTestUtils;
import bio.terra.workspace.connected.LandingZoneTestUtils;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.resource.controlled.cloud.azure.TestLandingZoneManager;
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

/** Base class for Azure connected tests: connected to Azure */
@ActiveProfiles({"azure-test", "connected-test"})
public class BaseAzureConnectedTest extends BaseSpringBootTest {

  @MockBean private SpendProfileService mockSpendProfileService;

  @Autowired protected AzureTestUtils azureTestUtils;
  @Autowired protected AzureConnectedTestUtils azureUtils;
  @Autowired private LandingZoneTestUtils landingZoneTestUtils;
  @Autowired private LandingZoneDao landingZoneDao;

  protected TestLandingZoneManager testLandingZoneManager;
  protected UUID landingZoneId;

  public SpendProfileService mockSpendProfileService() {
    return mockSpendProfileService;
  }

  /**
   * Creates a workspace and associated cloud context, mocking the dependency on
   * SpendProfileService.
   */
  protected Workspace createWorkspaceWithCloudContext(
      WorkspaceService workspaceService, AuthenticatedUserRequest userRequest)
      throws InterruptedException {
    initSpendProfileMock();
    createLandingZone();

    Workspace workspace = azureTestUtils.createWorkspace(workspaceService);
    azureUtils.createCloudContext(workspace.getWorkspaceId(), userRequest);
    return workspace;
  }

  protected void createLandingZone() {
    // create quasi landing zone with no resources, tests can add any they need
    landingZoneId = UUID.fromString(landingZoneTestUtils.getDefaultLandingZoneId());
    testLandingZoneManager = new TestLandingZoneManager(landingZoneDao, azureTestUtils);
    testLandingZoneManager.createLandingZoneDbRecord(landingZoneId);
  }

  /**
   * Mocks the dependency on SpendProfileService (BPM) required for Azure CloudContexts when BPM is
   * enabled.
   */
  protected SpendProfileId initSpendProfileMock() {
    Mockito.when(
            mockSpendProfileService()
                .authorizeLinking(Mockito.eq(azureTestUtils.getSpendProfileId()), Mockito.any()))
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
