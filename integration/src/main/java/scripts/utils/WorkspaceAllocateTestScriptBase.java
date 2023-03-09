package scripts.utils;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

import bio.terra.testrunner.runner.config.TestUserSpecification;
import bio.terra.workspace.api.WorkspaceApi;
import bio.terra.workspace.client.ApiException;
import bio.terra.workspace.model.CreateWorkspaceRequestBody;
import bio.terra.workspace.model.CreatedWorkspace;
import bio.terra.workspace.model.Properties;
import bio.terra.workspace.model.Property;
import bio.terra.workspace.model.WorkspaceStageModel;
import bio.terra.workspace.model.WsmPolicyInputs;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Fixture for tests that use a single workspace as a fixture. The expectation is that the workspace
 * setup & destruction are all that needs to happen in setup() and cleanup(), although doSetup() and
 * doCleanup() can still be overridden, provided the caller calls super().
 *
 * <p>This fixture always creates a stage MC_WORKSPACE. You must specify the spend profile to use as
 * the "spend-profile-id" parameter in the test configuration.
 *
 * <p>No doUserJourney() implementation is provided, and this must be overridden by inheriting
 * classes.
 */
public abstract class WorkspaceAllocateTestScriptBase extends WorkspaceApiTestScriptBase {
  private UUID workspaceUuid;
  private String spendProfileId;

  /**
   * Allow inheriting classes to obtain the workspace ID for the fixture.
   *
   * @return workspace UUID
   */
  protected UUID getWorkspaceId() {
    return workspaceUuid;
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
  public void setParametersMap(Map<String, String> parametersMap) throws Exception {
    super.setParametersMap(parametersMap);
    spendProfileId = ParameterUtils.getSpendProfile(parametersMap);
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
    workspaceUuid = UUID.randomUUID();
    createWorkspace(workspaceUuid, spendProfileId, workspaceApi);
  }

  /**
   * Utility for making the WSM calls to create a workspace. Exposed as protected for test
   * implementations which need to create additional workspaces.
   */
  protected CreatedWorkspace createWorkspace(
      UUID workspaceUuid, String spendProfileId, WorkspaceApi workspaceApi) throws Exception {
    return createWorkspaceWithPolicy(workspaceUuid, spendProfileId, workspaceApi, null);
  }

  protected CreatedWorkspace createWorkspaceWithPolicy(
      UUID workspaceUuid,
      String spendProfileId,
      WorkspaceApi workspaceApi,
      WsmPolicyInputs policyInputs)
      throws Exception {
    Properties properties = new Properties();
    Property property1 = new Property().key("foo").value("bar");
    Property property2 = new Property().key("xyzzy").value("plohg");
    properties.add(property1);
    properties.add(property2);

    final var requestBody =
        new CreateWorkspaceRequestBody()
            .id(workspaceUuid)
            .policies(policyInputs)
            .spendProfile(spendProfileId)
            .stage(getStageModel())
            .properties(properties);
    final CreatedWorkspace workspace = workspaceApi.createWorkspace(requestBody);
    assertThat(workspace.getId(), equalTo(workspaceUuid));
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
    workspaceApi.deleteWorkspace(workspaceUuid);
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
