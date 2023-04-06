package bio.terra.workspace.common;

import bio.terra.workspace.common.fixtures.WorkspaceFixtures;
import bio.terra.workspace.connected.UserAccessUtils;
import bio.terra.workspace.db.WorkspaceDao;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.resource.controlled.cloud.azure.TestLandingZoneManager;
import bio.terra.workspace.service.workspace.WorkspaceService;
import bio.terra.workspace.service.workspace.model.Workspace;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;

/** Base class for AWS tests. Treat these as connected tests: connected to AWS */
@Tag("aws")
@ActiveProfiles({"aws-test", "connected-test"})
public class BaseAwsConnectedTest extends BaseTest {

  @Autowired private UserAccessUtils userAccessUtils;
  @Autowired private WorkspaceDao workspaceDao;



  /**
   * Creates a workspace and associated cloud context, mocking the dependency on
   * SpendProfileService.
   */
  protected Workspace createWorkspaceWithCloudContext(
      WorkspaceService workspaceService, AuthenticatedUserRequest userRequest)
      throws InterruptedException {
//    Workspace workspace = azureTestUtils.createWorkspace(workspaceService);

    Workspace workspace =
        WorkspaceFixtures.defaultWorkspaceBuilder(null).build();
    workspaceService.createWorkspace(
        workspace, null, null, userAccessUtils.defaultUserAuthRequest());
    return workspace;



    // create quasi landing zone with no resources, tests can add any they need
    landingZoneId = UUID.fromString(landingZoneTestUtils.getDefaultLandingZoneId());
    testLandingZoneManager =
        new TestLandingZoneManager(landingZoneDao, workspaceDao, azureTestUtils);
    testLandingZoneManager.createLandingZoneDbRecord(landingZoneId);

    azureUtils.createCloudContext(workspace.getWorkspaceId(), userRequest);
    return workspace;
  }
}
