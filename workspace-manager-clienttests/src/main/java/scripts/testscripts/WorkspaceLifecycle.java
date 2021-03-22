package scripts.testscripts;

import bio.terra.testrunner.runner.config.TestUserSpecification;
import bio.terra.workspace.api.WorkspaceApi;
import bio.terra.workspace.client.ApiException;
import bio.terra.workspace.model.CreateWorkspaceRequestBody;
import bio.terra.workspace.model.UpdateWorkspaceRequestBody;
import bio.terra.workspace.model.WorkspaceDescription;
import bio.terra.workspace.model.WorkspaceStageModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scripts.utils.ClientTestUtils;

import java.util.UUID;
import scripts.utils.WorkspaceTestScriptBase;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

public class WorkspaceLifecycle extends WorkspaceTestScriptBase {
  private static final Logger logger = LoggerFactory.getLogger(WorkspaceLifecycle.class);

  @Override
  public void doUserJourney(TestUserSpecification testUser, WorkspaceApi workspaceApi) throws ApiException {
    UUID workspaceId = UUID.randomUUID();
    CreateWorkspaceRequestBody requestBody = new CreateWorkspaceRequestBody().id(workspaceId).stage(WorkspaceStageModel.MC_WORKSPACE);
    workspaceApi.createWorkspace(requestBody);
    ClientTestUtils.assertHttpSuccess(workspaceApi, "CREATE workspace");

    WorkspaceDescription workspaceDescription = workspaceApi.getWorkspace(workspaceId);
    ClientTestUtils.assertHttpSuccess(workspaceApi, "GET workspace");
    assertThat(workspaceDescription.getId(), equalTo(workspaceId));
    assertThat(workspaceDescription.getStage(), equalTo(WorkspaceStageModel.MC_WORKSPACE));

    UpdateWorkspaceRequestBody updateBody = new UpdateWorkspaceRequestBody().displayName("name").description("desc");
    WorkspaceDescription updatedDescription = workspaceApi.updateWorkspace(updateBody, workspaceId);
    ClientTestUtils.assertHttpSuccess(workspaceApi, "PATCH workspace");
    assertThat(updatedDescription.getDisplayName(), equalTo("name"));
    assertThat(updatedDescription.getDescription(), equalTo("desc"));

    workspaceApi.deleteWorkspace(workspaceId);
    ClientTestUtils.assertHttpSuccess(workspaceApi, "DELETE workspace");
  }
}
