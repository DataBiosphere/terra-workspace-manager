package scripts.utils;

import bio.terra.testrunner.runner.config.TestUserSpecification;
import bio.terra.workspace.api.WorkspaceApi;
import java.util.List;
import java.util.UUID;

/**
 * Fixture for tests that require two workspaces with GCP contexts, which allows for cloning
 * resources across workspaces. Both workspaces are owned by the same test user.
 *
 * <p>Per WorkspaceAllocateTestScriptBase, this fixture always creates a stage MC_WORKSPACE. You
 * must specify the spend profile to use as the "spend-profile-id" parameter in the test
 * configuration.
 *
 * <p>No doUserJourney() implementation is provided, and this must be overridden by inheriting
 * classes.
 */
public abstract class GcpWorkspaceCloneTestScriptBase extends WorkspaceAllocateTestScriptBase {

  private String sourceProjectId;
  private UUID destinationWorkspaceId;
  private String destinationProjectId;

  protected String getSourceProjectId() {
    return sourceProjectId;
  }

  protected UUID getDestinationWorkspaceId() {
    return destinationWorkspaceId;
  }

  protected String getDestinationProjectId() {
    return destinationProjectId;
  }

  /**
   * Create a GCP context for the source workspace created by the base class, and create a second
   * workspace with another GCP context.
   *
   * @param testUsers - test user configurations
   * @param workspaceApi - API with workspace methods
   * @throws Exception whatever checked exceptions get thrown
   */
  @Override
  protected void doSetup(List<TestUserSpecification> testUsers, WorkspaceApi workspaceApi)
      throws Exception {
    super.doSetup(testUsers, workspaceApi);
    sourceProjectId = CloudContextMaker.createGcpCloudContext(getWorkspaceId(), workspaceApi);
    destinationWorkspaceId = UUID.randomUUID();
    createWorkspace(destinationWorkspaceId, getSpendProfileId(), workspaceApi);
    destinationProjectId =
        CloudContextMaker.createGcpCloudContext(destinationWorkspaceId, workspaceApi);
  }

  /** Clean up source and destination workspaces. */
  @Override
  protected void doCleanup(List<TestUserSpecification> testUsers, WorkspaceApi workspaceApi)
      throws Exception {
    // Base class cleans up source workspace.
    super.doCleanup(testUsers, workspaceApi);
    workspaceApi.deleteWorkspace(destinationWorkspaceId);
  }
}
