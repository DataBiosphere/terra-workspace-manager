package scripts.utils;

import static org.hamcrest.MatcherAssert.assertThat;

import bio.terra.testrunner.runner.TestScript;
import bio.terra.testrunner.runner.config.TestUserSpecification;
import bio.terra.workspace.api.WorkspaceApi;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Intermediate base class for testing Workspace Manager in the TestRunner framework. This class
 * handles obtaining the WorkspaceApi object and basic checks and debugging around its status code.
 *
 * <p>Users of this class need to o verload doUserJourney() with the body of the userJourney()
 * method, and optionally doSetup() and doCleanup() aw well.
 */
public abstract class WorkspaceApiTestScriptBase extends TestScript {
  private static final Logger logger = LoggerFactory.getLogger(WorkspaceApiTestScriptBase.class);

  @Override
  public void setup(List<TestUserSpecification> testUsers) throws Exception {
    assertThat(
        "There must be at least one test user in configs/testusers directory.",
        testUsers != null && testUsers.size() > 0);
    final WorkspaceApi workspaceApi = ClientTestUtils.getWorkspaceClient(testUsers.get(0), server);
    try {
      doSetup(testUsers, workspaceApi);
    } catch (Exception ex) {
      logger.debug("Caught exception during setup ", ex);
      throw (ex);
    }
  }

  @Override
  public void userJourney(TestUserSpecification testUser) throws Exception {
    final WorkspaceApi workspaceApi = ClientTestUtils.getWorkspaceClient(testUser, server);
    try {
      doUserJourney(testUser, workspaceApi);
    } catch (Exception ex) {
      logger.debug("Caught exception in userJourney ", ex);
      throw (ex);
    }
  }

  @Override
  public void cleanup(List<TestUserSpecification> testUsers) throws Exception {
    assertThat(
        "There must be at least one test user in configs/testusers directory.",
        testUsers != null && testUsers.size() > 0);
    final WorkspaceApi workspaceApi = ClientTestUtils.getWorkspaceClient(testUsers.get(0), server);
    try {
      doCleanup(testUsers, workspaceApi);
    } catch (Exception ex) {
      logger.debug("Caught exception during cleanup ", ex);
      throw (ex);
    }
  }

  protected void doSetup(List<TestUserSpecification> testUsers, WorkspaceApi workspaceApi)
      throws Exception {}

  // doUserJourney is the only method we force the class to override, so we don't get trivially
  // passing tests that forget to have a body
  protected abstract void doUserJourney(TestUserSpecification testUser, WorkspaceApi workspaceApi)
      throws Exception;

  protected void doCleanup(List<TestUserSpecification> testUsers, WorkspaceApi workspaceApi)
      throws Exception {}
}
