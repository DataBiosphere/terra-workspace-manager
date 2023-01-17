package scripts.utils;

import static org.hamcrest.MatcherAssert.assertThat;

import bio.terra.testrunner.runner.config.TestUserSpecification;
import bio.terra.workspace.api.WorkspaceApi;
import bio.terra.workspace.model.IamRole;
import java.util.List;
import java.util.UUID;

/**
 * Fixture for tests that require two workspaces with GCP contexts, which allows for cloning
 * resources across workspaces. This requires two test users. The first user owns the first
 * workspace, while the second user is a reader in the first workspace and an owner of the second.
 *
 * <p>Per WorkspaceAllocateTestScriptBase, this fixture always creates a stage MC_WORKSPACE. You
 * must specify the spend profile to use as the "spend-profile-id" parameter in the test
 * configuration.
 */
public abstract class GcpWorkspaceCloneTestScriptBase extends WorkspaceAllocateTestScriptBase {

  private String sourceProjectId;
  private TestUserSpecification reader;
  private UUID destinationWorkspaceId;
  private String destinationProjectId;

  protected String getSourceProjectId() {
    return sourceProjectId;
  }

  protected TestUserSpecification getWorkspaceReader() {
    return reader;
  }

  protected UUID getDestinationWorkspaceId() {
    return destinationWorkspaceId;
  }

  protected String getDestinationProjectId() {
    return destinationProjectId;
  }

  /**
   * Create a GCP context for the source workspace created by the base class, add reader to the
   * first workspace, and create a second workspace with another GCP context.
   *
   * @param testUsers - test user configurations
   * @param workspaceApi - API with workspace methods
   * @throws Exception whatever checked exceptions get thrown
   */
  @Override
  protected void doSetup(List<TestUserSpecification> testUsers, WorkspaceApi workspaceApi)
      throws Exception {
    super.doSetup(testUsers, workspaceApi);
    assertThat(
        "There must be at least two test users defined for this test.",
        testUsers != null && testUsers.size() > 1);
    reader = testUsers.get(1);
    ClientTestUtils.grantRole(workspaceApi, getWorkspaceId(), reader, IamRole.READER);
    sourceProjectId = CloudContextMaker.createGcpCloudContext(getWorkspaceId(), workspaceApi);
    destinationWorkspaceId = UUID.randomUUID();
    WorkspaceApi secondUserWorkspaceApi = ClientTestUtils.getWorkspaceClient(reader, server);
    createWorkspace(destinationWorkspaceId, getSpendProfileId(), secondUserWorkspaceApi);
    destinationProjectId =
        CloudContextMaker.createGcpCloudContext(destinationWorkspaceId, secondUserWorkspaceApi);
  }

  /** Clean up source and destination workspaces. */
  @Override
  protected void doCleanup(List<TestUserSpecification> testUsers, WorkspaceApi workspaceApi)
      throws Exception {
    // Base class cleans up source workspace.
    super.doCleanup(testUsers, workspaceApi);
    // Destination workspace is owner by reader, so they need to clean it up.
    WorkspaceApi secondUserWorkspaceApi = ClientTestUtils.getWorkspaceClient(reader, server);
    secondUserWorkspaceApi.deleteWorkspace(destinationWorkspaceId);
  }
}
