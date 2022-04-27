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
  private static final String invalidUserFacingId = "User facing id";
  private static final String validUserFacingId = "user-facing-id";
  private static final String validUserFacingId2 = "user-facing-id-2";
  private static final String workspaceName = "name";
  private static final String workspaceDescriptionString = "description";

  @Override
  public void doUserJourney(TestUserSpecification testUser, WorkspaceApi workspaceApi)
      throws ApiException {
    UUID workspaceId = UUID.randomUUID();
    CreateWorkspaceRequestBody createBody =
        new CreateWorkspaceRequestBody()
            .id(workspaceId)
            .userFacingId(invalidUserFacingId)
            .stage(WorkspaceStageModel.MC_WORKSPACE);

    ApiException ex =
        assertThrows(ApiException.class, () -> workspaceApi.createWorkspace(createBody));
    assertThat(
        ex.getMessage(),
        containsString(
            "ID must have 3-63 characters, contain lowercase letters, numbers, dashes, or underscores, and start with lowercase letter"));

    createBody.userFacingId(validUserFacingId);
    workspaceApi.createWorkspace(createBody);
    ClientTestUtils.assertHttpSuccess(workspaceApi, "CREATE workspace");

    WorkspaceDescription workspaceDescription = workspaceApi.getWorkspace(workspaceId);
    ClientTestUtils.assertHttpSuccess(workspaceApi, "GET workspace");
    assertThat(workspaceDescription.getId(), equalTo(workspaceId));
    assertThat(workspaceDescription.getStage(), equalTo(WorkspaceStageModel.MC_WORKSPACE));

    UpdateWorkspaceRequestBody updateBody =
        new UpdateWorkspaceRequestBody()
            .userFacingId(invalidUserFacingId)
            .displayName(workspaceName)
            .description(workspaceDescriptionString);
    ex =
        assertThrows(
            ApiException.class, () -> workspaceApi.updateWorkspace(updateBody, workspaceId));
    assertThat(
        ex.getMessage(),
        containsString(
            "ID must have 3-63 characters, contain lowercase letters, numbers, dashes, or underscores, and start with lowercase letter"));

    updateBody.userFacingId(validUserFacingId2);
    WorkspaceDescription updatedDescription = workspaceApi.updateWorkspace(updateBody, workspaceId);
    ClientTestUtils.assertHttpSuccess(workspaceApi, "PATCH workspace");
    assertThat(updatedDescription.getUserFacingId(), equalTo(validUserFacingId2));
    assertThat(updatedDescription.getDisplayName(), equalTo(workspaceName));
    assertThat(updatedDescription.getDescription(), equalTo(workspaceDescriptionString));

    workspaceApi.deleteWorkspace(workspaceId);
    ClientTestUtils.assertHttpSuccess(workspaceApi, "DELETE workspace");
  }
}
