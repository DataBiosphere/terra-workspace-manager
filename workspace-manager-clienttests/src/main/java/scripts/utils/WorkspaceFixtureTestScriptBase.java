package scripts.utils;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

import bio.terra.testrunner.runner.config.TestUserSpecification;
import bio.terra.workspace.api.WorkspaceApi;
import bio.terra.workspace.client.ApiException;
import bio.terra.workspace.model.CreateWorkspaceRequestBody;
import bio.terra.workspace.model.CreatedWorkspace;
import bio.terra.workspace.model.WorkspaceDescription;
import bio.terra.workspace.model.WorkspaceStageModel;
import java.util.List;
import java.util.UUID;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scripts.testscripts.GetWorkspace;

/**
 * Base class for tests that use a single workspace as a fixture. The expectation is that
 * the workspace setup & destruction are all that needs to happen in setup() and cleanup(), although
 * doSetup() and doCleanup() can still be overridden, provided the caller calls super().
 *
 * No doUserJourney() implementation is provided, and this must be overridden by inheriting classes.
 */
public abstract class WorkspaceFixtureTestScriptBase extends WorkspaceTestScriptBase {
  private static final Logger logger = LoggerFactory.getLogger(GetWorkspace.class);

  private UUID workspaceId;

  /**
   * Allow inheriting classes to obtain the workspace ID for the fixture.
   * @return
   */
  protected UUID getWorkspaceId() {
    return workspaceId;
  }

  /**
   * Create a workspace as a test fixture (i.e. not specifically the code under test).
   * @param testUsers - test user configurations
   * @param workspaceApi - API with workspace methods
   * @throws ApiException
   */
  @Override
  protected void doSetup(List<TestUserSpecification> testUsers, @NotNull WorkspaceApi workspaceApi)
      throws ApiException {
    workspaceId = UUID.randomUUID();
    final var requestBody = new CreateWorkspaceRequestBody()
        .id(workspaceId)
        .stage(getStageModel());
    final CreatedWorkspace workspace = workspaceApi.createWorkspace(requestBody);
    assertThat(workspace.getId(), equalTo(workspaceId));
  }

  /**
   * Clean up workspace only.
   * @param testUsers
   * @param workspaceApi
   * @throws ApiException
   */
  @Override
  protected void doCleanup(List<TestUserSpecification> testUsers, @NotNull WorkspaceApi workspaceApi)
      throws ApiException {
    workspaceApi.deleteWorkspace(workspaceId);
  }

  /**
   * Override this method to change the stage model of the workspace. Preserves default
   * of RAWLS_WORKSPACE.
   * @return the stage model to be used in create
   */
  protected WorkspaceStageModel getStageModel() {
    return WorkspaceStageModel.RAWLS_WORKSPACE;
  }
}
