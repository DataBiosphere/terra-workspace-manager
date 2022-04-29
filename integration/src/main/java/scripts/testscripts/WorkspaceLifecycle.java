package scripts.testscripts;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertThrows;

import bio.terra.testrunner.runner.config.TestUserSpecification;
import bio.terra.workspace.api.WorkspaceApi;
import bio.terra.workspace.client.ApiException;
import bio.terra.workspace.model.CreateWorkspaceRequestBody;
import bio.terra.workspace.model.UpdateWorkspaceRequestBody;
import bio.terra.workspace.model.WorkspaceDescription;
import bio.terra.workspace.model.WorkspaceStageModel;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scripts.utils.ClientTestUtils;
import scripts.utils.WorkspaceApiTestScriptBase;

public class WorkspaceLifecycle extends WorkspaceApiTestScriptBase {
  private static final Logger logger = LoggerFactory.getLogger(WorkspaceLifecycle.class);

  // Perf tests run same test in different threads. We need to make userFacingId unique. We can't
  // use UUID.randomUuid(); that returns the same uuid for different threads in a JVM. See
  // https://issues.apache.org/jira/browse/SPARK-23599 and
  // https://stackoverflow.com/questions/31361833/same-uuid-being-generated-in-multi-threaded-application
  private static final String threadId = String.valueOf(Thread.currentThread().getId());

  private static final String INVALID_USER_FACING_ID = "User facing id" + threadId;
  private static final String VALID_USER_FACING_ID = "user-facing-id" + threadId;
  private static final String VALID_USER_FACING_ID_2 = "user-facing-id-2" + threadId;

  private static final String WORKSPACE_NAME = "name";
  private static final String WORKSPACE_DESCRIPTION = "description";

  @Override
  public void doUserJourney(TestUserSpecification testUser, WorkspaceApi workspaceApi)
      throws ApiException {
    UUID workspaceUuid = UUID.randomUUID();
    CreateWorkspaceRequestBody createBody =
        new CreateWorkspaceRequestBody()
            .id(workspaceUuid)
            .userFacingId(INVALID_USER_FACING_ID)
            .stage(WorkspaceStageModel.MC_WORKSPACE);

    ApiException ex =
        assertThrows(ApiException.class, () -> workspaceApi.createWorkspace(createBody));
    assertThat(
        ex.getMessage(),
        containsString(
            "ID must have 3-63 characters, contain lowercase letters, numbers, dashes, or underscores, and start with lowercase letter"));

    createBody.userFacingId(VALID_USER_FACING_ID);
    workspaceApi.createWorkspace(createBody);
    ClientTestUtils.assertHttpSuccess(workspaceApi, "CREATE workspace");

    WorkspaceDescription workspaceDescription = workspaceApi.getWorkspace(workspaceUuid);
    ClientTestUtils.assertHttpSuccess(workspaceApi, "GET workspace");
    assertThat(workspaceDescription.getId(), equalTo(workspaceUuid));
    assertThat(workspaceDescription.getStage(), equalTo(WorkspaceStageModel.MC_WORKSPACE));

    UpdateWorkspaceRequestBody updateBody =
        new UpdateWorkspaceRequestBody()
            .userFacingId(INVALID_USER_FACING_ID)
            .displayName(WORKSPACE_NAME)
            .description(WORKSPACE_DESCRIPTION);
    ex =
        assertThrows(
            ApiException.class, () -> workspaceApi.updateWorkspace(updateBody, workspaceUuid));
    assertThat(
        ex.getMessage(),
        containsString(
            "ID must have 3-63 characters, contain lowercase letters, numbers, dashes, or underscores, and start with lowercase letter"));

    updateBody.userFacingId(VALID_USER_FACING_ID_2);
    WorkspaceDescription updatedDescription =
        workspaceApi.updateWorkspace(updateBody, workspaceUuid);
    ClientTestUtils.assertHttpSuccess(workspaceApi, "PATCH workspace");
    assertThat(updatedDescription.getUserFacingId(), equalTo(VALID_USER_FACING_ID_2));
    assertThat(updatedDescription.getDisplayName(), equalTo(WORKSPACE_NAME));
    assertThat(updatedDescription.getDescription(), equalTo(WORKSPACE_DESCRIPTION));

    workspaceApi.deleteWorkspace(workspaceUuid);
    ClientTestUtils.assertHttpSuccess(workspaceApi, "DELETE workspace");
  }
}
