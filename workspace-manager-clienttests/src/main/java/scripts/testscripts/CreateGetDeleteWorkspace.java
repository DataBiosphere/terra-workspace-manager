package scripts.testscripts;

import bio.terra.testrunner.runner.TestScript;
import bio.terra.testrunner.runner.config.TestUserSpecification;
import bio.terra.workspace.client.ApiClient;
import bio.terra.workspace.api.WorkspaceApi;
import bio.terra.workspace.client.ApiException;
import bio.terra.workspace.model.CreateWorkspaceRequestBody;
import bio.terra.workspace.model.CreatedWorkspace;
import bio.terra.workspace.model.WorkspaceDescription;
import bio.terra.workspace.model.WorkspaceStageModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scripts.utils.WorkspaceManagerServiceUtils;

import java.util.List;
import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

public class CreateGetDeleteWorkspace extends TestScript {
  private static final Logger logger = LoggerFactory.getLogger(CreateGetDeleteWorkspace.class);
  private CreatedWorkspace workspace;

  @Override
  public void setup(List<TestUserSpecification> testUsers) throws Exception {
    assertThat("There must be at least one test user in configs/testusers directory.", testUsers!=null && testUsers.size()>0);
  }

  @Override
  public void userJourney(TestUserSpecification testUser) throws Exception {
    UUID id = UUID.randomUUID();
    ApiClient apiClient = WorkspaceManagerServiceUtils.getClientForTestUser(testUser, server);
    WorkspaceApi workspaceApi = new WorkspaceApi(apiClient);
    try {
      CreateWorkspaceRequestBody requestBody = new CreateWorkspaceRequestBody();
      requestBody.setId(id);
      workspace = workspaceApi.createWorkspace(requestBody);
    } catch (ApiException apiEx) {
      logger.debug("Caught exception creating workspace ", apiEx);
    }
    int httpCode = workspaceApi.getApiClient().getStatusCode();
    logger.info("CREATE workspace HTTP code: {}", httpCode);
    assertThat(httpCode, equalTo(200));
    assertThat(workspace.getId(), equalTo(id));

    WorkspaceDescription workspaceDescription = workspaceApi.getWorkspace(id);
    httpCode = workspaceApi.getApiClient().getStatusCode();
    logger.info("GET workspace HTTP code: {}", httpCode);
    assertThat(httpCode, equalTo(200));
    assertThat(workspaceDescription.getId(), equalTo(id));
    assertThat(workspaceDescription.getStage(), equalTo(WorkspaceStageModel.RAWLS_WORKSPACE));

    workspaceApi.deleteWorkspace(id);
    httpCode = workspaceApi.getApiClient().getStatusCode();
    logger.info("DELETE workspace HTTP code: {}", httpCode);
    assertThat(httpCode, equalTo(204));
  }

  @Override
  public void cleanup(List<TestUserSpecification> testUsers) throws Exception {
    assertThat("There must be at least one test user in configs/testusers directory.", testUsers!=null && testUsers.size()>0);
  }
}
