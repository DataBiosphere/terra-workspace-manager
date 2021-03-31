package scripts.utils;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

import bio.terra.testrunner.runner.config.TestUserSpecification;
import bio.terra.workspace.api.WorkspaceApi;
import bio.terra.workspace.client.ApiException;
import bio.terra.workspace.model.CloudPlatform;
import bio.terra.workspace.model.CreateCloudContextRequest;
import bio.terra.workspace.model.CreateCloudContextResult;
import bio.terra.workspace.model.JobControl;
import bio.terra.workspace.model.JobReport.StatusEnum;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Fixture for tests that use a single workspace with a GCP cloud context as a fixture. The
 * expectation is that the workspace and context setup & deletion are all that needs to happen in
 * setup() and cleanup(), although doSetup() and doCleanup() can still be overridden, provided the
 * caller calls super().
 *
 * <p>This fixture always creates a stage MC_WORKSPACE. You must specify the spend profile to use as
 * the first parameter in the test configuration.
 *
 * <p>No doUserJourney() implementation is provided, and this must be overridden by inheriting
 * classes.
 */
public abstract class GcpCloudContextTestScriptBase extends WorkspaceAllocateTestScriptBase {

  private static int CREATE_CONTEXT_POLL_SECONDS = 5;

  private String projectId;

  protected String getProjectId() {
    return projectId;
  }

  /**
   * Create a spend profile in the workspace test fixture (i.e. not specifically the code under
   * test).
   *
   * @param testUsers - test user configurations
   * @param workspaceApi - API with workspace methods
   * @throws Exception whatever checked exceptions get thrown
   */
  @Override
  protected void doSetup(List<TestUserSpecification> testUsers, WorkspaceApi workspaceApi)
      throws Exception {
    super.doSetup(testUsers, workspaceApi);
    String createContextJobId = UUID.randomUUID().toString();
    final var requestBody =
        new CreateCloudContextRequest()
            .jobControl(new JobControl().id(createContextJobId))
            .cloudPlatform(CloudPlatform.GCP);
    CreateCloudContextResult result =
        workspaceApi.createCloudContext(requestBody, getWorkspaceId());
    while (ClientTestUtils.jobIsRunning(result.getJobReport())) {
      TimeUnit.SECONDS.sleep(CREATE_CONTEXT_POLL_SECONDS);
      result = workspaceApi.getCreateCloudContextResult(getWorkspaceId(), createContextJobId);
    }
    assertThat(result.getJobReport().getStatus(), equalTo(StatusEnum.SUCCEEDED));
    projectId = result.getGcpContext().getProjectId();
  }

  /**
   * Clean up the context first, then delete the workspace via super cleanup method.
   *
   * <p>This is not strictly necessary as deleting a workspace also deletes the context, but it
   * ensures we exercise the deleteContext endpoint.
   */
  @Override
  protected void doCleanup(List<TestUserSpecification> testUsers, WorkspaceApi workspaceApi)
      throws ApiException {
    workspaceApi.deleteCloudContext(getWorkspaceId(), CloudPlatform.GCP);
    super.doCleanup(testUsers, workspaceApi);
  }
}
