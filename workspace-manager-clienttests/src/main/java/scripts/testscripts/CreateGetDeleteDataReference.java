package scripts.testscripts;

import bio.terra.testrunner.runner.TestScript;
import bio.terra.testrunner.runner.config.TestUserSpecification;
import bio.terra.workspace.client.ApiClient;
import bio.terra.workspace.api.WorkspaceApi;
import bio.terra.workspace.client.ApiException;
import bio.terra.workspace.model.CloningInstructionsEnum;
import bio.terra.workspace.model.CreateDataReferenceRequestBody;
import bio.terra.workspace.model.CreateWorkspaceRequestBody;
import bio.terra.workspace.model.CreatedWorkspace;
import bio.terra.workspace.model.DataReferenceDescription;
import bio.terra.workspace.model.DataRepoSnapshot;
import bio.terra.workspace.model.ReferenceTypeEnum;
import bio.terra.workspace.model.WorkspaceDescription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scripts.utils.DataReferenceUtils;
import scripts.utils.WorkspaceManagerServiceUtils;

import java.util.List;
import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

public class CreateGetDeleteDataReference extends TestScript {
  private static final Logger logger = LoggerFactory.getLogger(CreateGetDeleteDataReference.class);
  private UUID id;
  private CreatedWorkspace workspace;

  @Override
  public void setup(List<TestUserSpecification> testUsers) throws Exception {
    assertThat("There must be at least one test user in configs/testusers directory.", testUsers!=null && testUsers.size()>0);
    id = UUID.randomUUID();
    ApiClient apiClient = WorkspaceManagerServiceUtils.getClientForTestUser(testUsers.get(0), server);
    WorkspaceApi workspaceApi = new WorkspaceApi(apiClient);
    try {
      CreateWorkspaceRequestBody requestBody = new CreateWorkspaceRequestBody().id(id);
      workspace = workspaceApi.createWorkspace(requestBody);
    } catch (ApiException apiEx) {
      logger.debug("Caught exception creating workspace ", apiEx);
    }

    int httpCode = workspaceApi.getApiClient().getStatusCode();
    logger.info("CREATE workspace HTTP code: {}", httpCode);
    assertThat(httpCode, equalTo(200));
  }

  @Override
  public void userJourney(TestUserSpecification testUser) throws Exception {
    ApiClient apiClient = WorkspaceManagerServiceUtils.getClientForTestUser(testUser, server);
    WorkspaceApi workspaceApi = new WorkspaceApi(apiClient);
    CreateDataReferenceRequestBody referenceRequest = DataReferenceUtils
        .defaultDataReferenceRequest();
    DataReferenceDescription createdReferenceDescription = workspaceApi.createDataReference(referenceRequest, id);
    int httpCode = workspaceApi.getApiClient().getStatusCode();
    logger.info("CREATE data reference HTTP code: {}", httpCode);
    assertThat(httpCode, equalTo(200));

    DataReferenceDescription getResult = workspaceApi.getDataReference(id, createdReferenceDescription.getReferenceId());
    httpCode = workspaceApi.getApiClient().getStatusCode();
    logger.info("GET data reference HTTP code: {}", httpCode);
    assertThat(httpCode, equalTo(200));
    assertThat(getResult, equalTo(createdReferenceDescription));

    workspaceApi.deleteDataReference(id, getResult.getReferenceId());
    httpCode = workspaceApi.getApiClient().getStatusCode();
    logger.info("DELETE data reference HTTP code: {}", httpCode);
    assertThat(httpCode, equalTo(204));
  }

  @Override
  public void cleanup(List<TestUserSpecification> testUsers) throws Exception {
    assertThat("There must be at least one test user in configs/testusers directory.", testUsers!=null && testUsers.size()>0);
  }
}
