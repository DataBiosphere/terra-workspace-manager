package scripts.testscripts;

import bio.terra.testrunner.runner.config.TestUserSpecification;
import bio.terra.workspace.api.WorkspaceApi;
import java.util.List;
import scripts.utils.ClientTestUtils;
import scripts.utils.WorkspaceAllocateTestScriptBase;

public class UpdateReferenceResources extends WorkspaceAllocateTestScriptBase {

  private TestUserSpecification userWithAccess;
  private TestUserSpecification userWithNoAccess;
  @Override
  protected void doSetup(
      List<TestUserSpecification> testUsers, WorkspaceApi sourceOwnerWorkspaceApi)
      throws Exception {

  }

  @Override
  protected void doCleanup(List<TestUserSpecification> testUsers, WorkspaceApi workspaceApi)
      throws Exception {
    super.doCleanup(testUsers, workspaceApi);
    // Destination workspace may only be deleted by the user who created it
    final WorkspaceApi destinationWorkspaceApi =
        ClientTestUtils.getWorkspaceClient(userWithAccess, server);
   // destinationWorkspaceApi.deleteWorkspace(destinationWorkspaceId);
  }

  @Override
  protected void doUserJourney(TestUserSpecification testUser, WorkspaceApi workspaceApi)
      throws Exception {

  }
}
