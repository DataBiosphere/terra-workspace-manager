package scripts.utils;

import static org.hamcrest.MatcherAssert.assertThat;

import bio.terra.testrunner.runner.TestScript;
import bio.terra.testrunner.runner.config.TestUserSpecification;
import bio.terra.workspace.api.WorkspaceApi;
import bio.terra.workspace.client.ApiException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class WorkspaceTestScriptBase extends TestScript {
  private static final Logger logger = LoggerFactory.getLogger(WorkspaceTestScriptBase.class);

  @Override
  public void setup(List<TestUserSpecification> testUsers) throws Exception {
    assertThat("There must be at least one test user in configs/testusers directory.",
        testUsers != null && testUsers.size() > 0);
    final WorkspaceApi workspaceApi = WorkspaceManagerServiceUtils
        .getWorkspaceClient(testUsers.get(0), server);
    try {
      doSetup(testUsers, workspaceApi);
    } catch (ApiException apiEx) {
      logger.debug("Caught exception creating setup ", apiEx);
      throw(apiEx);
    }
    WorkspaceManagerServiceUtils.assertHttpOk(workspaceApi, "setup");
  }

  @Override
  public void userJourney(TestUserSpecification testUser) throws Exception {
    final WorkspaceApi workspaceApi = WorkspaceManagerServiceUtils
        .getWorkspaceClient(testUser, server);
    try {
      doUserJourney(testUser, workspaceApi);
    } catch (ApiException apiEx) {
      logger.debug("Caught exception in userJourney ", apiEx);
      throw(apiEx);
    }
    WorkspaceManagerServiceUtils.assertHttpOk(workspaceApi, "userJourney");
  }

  @Override
  public void cleanup(List<TestUserSpecification> testUsers) throws Exception {
    assertThat("There must be at least one test user in configs/testusers directory.",
        testUsers != null && testUsers.size() > 0);
    final WorkspaceApi workspaceApi = WorkspaceManagerServiceUtils
        .getWorkspaceClient(testUsers.get(0), server);
    try {
      doCleanup(testUsers, workspaceApi);
    } catch (ApiException apiEx) {
      logger.debug("Caught exception during cleanup ", apiEx);
      throw(apiEx);
    }
    WorkspaceManagerServiceUtils.assertHttpOk(workspaceApi, "cleanup");
  }

  public void doSetup(List<TestUserSpecification> testUsers, WorkspaceApi workspaceApi) throws ApiException {
  }

  // doUserJourney is hte only method we force the class to override, so we don't get trivially passing tests that forget
  // to have a body
  public abstract void doUserJourney(TestUserSpecification testUser, WorkspaceApi workspaceApi) throws ApiException;

  public void doCleanup(List<TestUserSpecification> testUsers, WorkspaceApi workspaceApi) throws ApiException { };
}
