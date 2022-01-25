package scripts.utils;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

import bio.terra.testrunner.runner.config.TestUserSpecification;
import bio.terra.workspace.api.WorkspaceApi;
import bio.terra.workspace.client.ApiException;
import bio.terra.workspace.model.CreateWorkspaceRequestBody;
import bio.terra.workspace.model.CreatedWorkspace;
import bio.terra.workspace.model.WorkspaceStageModel;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Fixture for tests that use a single workspace as a fixture. The expectation is that the workspace
 * setup & destruction are all that needs to happen in setup() and cleanup(), although doSetup() and
 * doCleanup() can still be overridden, provided the caller calls super().
 *
 * <p>This fixture always creates a stage MC_WORKSPACE. You must specify the spend profile to use as
 * the first parameter in the test configuration.
 *
 * <p>No doUserJourney() implementation is provided, and this must be overridden by inheriting
 * classes.
 */
public abstract class WorkspaceAllocateTestScriptBase extends WorkspaceApiTestScriptBase {
  private UUID workspaceId;
  private String spendProfileId;

  /**
   * Allow inheriting classes to obtain the workspace ID for the fixture.
   *
   * @return workspace UUID
   */
  protected UUID getWorkspaceId() {
    return workspaceId;
  }

  /**
   * Allow inheriting classes to obtain the spend profile
   *
   * @return spend profile string
   */
  protected String getSpendProfileId() {
    return spendProfileId;
  }

  @Override
  public void setParameters(Map<String, String> parameters) throws Exception {
    super.setParameters(parameters);

    if (parameters == null || !parameters.containsKey(ParameterKeys.SPEND_PROFILE_PARAMETER)) {
      throw new IllegalArgumentException(
          "Must provide the spend profile id as parameter " + ParameterKeys.SPEND_PROFILE_PARAMETER);
    } else {
      spendProfileId = parameters.get(ParameterKeys.SPEND_PROFILE_PARAMETER);
    }
  }

  /**
   * Create a workspace as a test fixture (i.e. not specifically the code under test).
   *
   * @param testUsers - test user configurations
   * @param workspaceApi - API with workspace methods
   * @throws Exception whatever checked exceptions get thrown
   */
  @Override
  protected void doSetup(List<TestUserSpecification> testUsers, WorkspaceApi workspaceApi)
      throws Exception {
    workspaceId = UUID.randomUUID();
    createWorkspace(workspaceId, spendProfileId, workspaceApi);
  }

  /**
   * Utility for making the WSM calls to create a workspace. Exposed as protected for test
   * implementations which need to create additional workspaces.
   */
  protected CreatedWorkspace createWorkspace(
      UUID workspaceId, String spendProfileId, WorkspaceApi workspaceApi) throws Exception {
    final var requestBody =
        new CreateWorkspaceRequestBody()
            .id(workspaceId)
            .spendProfile(spendProfileId)
            .stage(getStageModel());
    final CreatedWorkspace workspace = workspaceApi.createWorkspace(requestBody);
    assertThat(workspace.getId(), equalTo(workspaceId));
    return workspace;
  }

  /**
   * Clean up workspace only.
   *
   * @param testUsers
   * @param workspaceApi
   * @throws ApiException
   */
  @Override
  protected void doCleanup(List<TestUserSpecification> testUsers, WorkspaceApi workspaceApi)
      throws Exception {
    workspaceApi.deleteWorkspace(workspaceId);
  }

  /**
   * Override this method to change the stage model of the workspace. Preserves default of
   * MC_WORKSPACE.
   *
   * @return the stage model to be used in create
   */
  protected WorkspaceStageModel getStageModel() {
    return WorkspaceStageModel.MC_WORKSPACE;
  }
}
