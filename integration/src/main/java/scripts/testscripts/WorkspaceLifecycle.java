package scripts.testscripts;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

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
  private static final String workspaceName = "name";
  private static final String workspaceDescriptionString = "description";

  @Override
  public void doUserJourney(TestUserSpecification testUser, WorkspaceApi workspaceApi)
      throws ApiException {
    UUID workspaceId = UUID.randomUUID();
    CreateWorkspaceRequestBody requestBody =
        new CreateWorkspaceRequestBody().id(workspaceId).stage(WorkspaceStageModel.MC_WORKSPACE);
    workspaceApi.createWorkspace(requestBody);
    ClientTestUtils.assertHttpSuccess(workspaceApi, "CREATE workspace");

    WorkspaceDescription workspaceDescription = workspaceApi.getWorkspace(workspaceId);
    ClientTestUtils.assertHttpSuccess(workspaceApi, "GET workspace");
    assertThat(workspaceDescription.getId(), equalTo(workspaceId));
    assertThat(workspaceDescription.getStage(), equalTo(WorkspaceStageModel.MC_WORKSPACE));

    UpdateWorkspaceRequestBody updateBody =
        new UpdateWorkspaceRequestBody()
            .displayName(workspaceName)
            .description(workspaceDescriptionString);
    WorkspaceDescription updatedDescription = workspaceApi.updateWorkspace(updateBody, workspaceId);
    ClientTestUtils.assertHttpSuccess(workspaceApi, "PATCH workspace");
    assertThat(updatedDescription.getDisplayName(), equalTo(workspaceName));
    assertThat(updatedDescription.getDescription(), equalTo(workspaceDescriptionString));

    workspaceApi.deleteWorkspace(workspaceId);
    ClientTestUtils.assertHttpSuccess(workspaceApi, "DELETE workspace");
  }
}
