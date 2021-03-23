package scripts.utils;

import static org.hamcrest.MatcherAssert.assertThat;

import bio.terra.testrunner.runner.TestScript;
import bio.terra.testrunner.runner.config.TestUserSpecification;
import bio.terra.workspace.api.WorkspaceApi;
import bio.terra.workspace.client.ApiException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Intermediate base class for testing Workspace Manager in the TestRunner framework. This class
 * handles obtaining the WorkspaceApi object and basic checks and debugging around its status code.
 *
 * <p>Users of this class need to o verload doUserJourney() with efssentially the body of the
 * userJourney() method, and optionally doSetup() and doCleanup() aw well.
 */
public abstract class WorkspaceTestScriptBase extends TestScript {
  private static final Logger logger = LoggerFactory.getLogger(WorkspaceTestScriptBase.class);

  @Override
  public void setup(List<TestUserSpecification> testUsers) throws Exception {
    assertThat(
        "There must be at least one test user in configs/testusers directory.",
        testUsers != null && testUsers.size() > 0);
    final WorkspaceApi workspaceApi = ClientTestUtils.getWorkspaceClient(testUsers.get(0), server);
    try {
      doSetup(testUsers, workspaceApi);
    } catch (ApiException apiEx) {
      logger.debug("Caught exception creating setup ", apiEx);
      throw (apiEx);
    }
  }

  @Override
  public void userJourney(TestUserSpecification testUser) throws Exception {
    final WorkspaceApi workspaceApi = ClientTestUtils.getWorkspaceClient(testUser, server);
    try {
      doUserJourney(testUser, workspaceApi);
    } catch (ApiException apiEx) {
      logger.debug("Caught exception in userJourney ", apiEx);
      throw (apiEx);
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
    } catch (ApiException apiEx) {
      logger.debug("Caught exception during cleanup ", apiEx);
      throw (apiEx);
    }
  }

  protected void doSetup(List<TestUserSpecification> testUsers, WorkspaceApi workspaceApi)
      throws ApiException {}

  // doUserJourney is the only method we force the class to override, so we don't get trivially
  // passing tests that forget
  // to have a body
  protected abstract void doUserJourney(TestUserSpecification testUser, WorkspaceApi workspaceApi)
      throws ApiException;

  protected void doCleanup(List<TestUserSpecification> testUsers, WorkspaceApi workspaceApi) throws ApiException {}
}
