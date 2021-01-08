package scripts.testscripts;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

import bio.terra.testrunner.runner.TestScript;
import bio.terra.testrunner.runner.config.TestUserSpecification;
import bio.terra.workspace.api.WorkspaceApi;
import bio.terra.workspace.client.ApiClient;
import bio.terra.workspace.client.ApiException;
import bio.terra.workspace.model.CreateDataReferenceRequestBody;
import bio.terra.workspace.model.CreateWorkspaceRequestBody;
import bio.terra.workspace.model.CreatedWorkspace;
import bio.terra.workspace.model.DataReferenceDescription;
import bio.terra.workspace.model.ReferenceTypeEnum;
import bio.terra.workspace.model.WorkspaceDescription;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scripts.utils.DataReferenceUtils;
import scripts.utils.WorkspaceManagerServiceUtils;

public class GetDataReferenceByName extends TestScript {
  private static final Logger logger = LoggerFactory.getLogger(GetDataReferenceByName.class);
  private UUID workspaceId;
  private DataReferenceDescription dataReference;

  @Override
  public void setup(List<TestUserSpecification> testUsers) throws Exception {
    assertThat("There must be at least one test user in configs/testusers directory.", testUsers!=null && testUsers.size()>0);
    workspaceId = UUID.randomUUID();
    ApiClient apiClient = WorkspaceManagerServiceUtils.getClientForTestUser(testUsers.get(0), server);
    WorkspaceApi workspaceApi = new WorkspaceApi(apiClient);
    // First, create a workspace.
    try {
      CreateWorkspaceRequestBody requestBody = new CreateWorkspaceRequestBody().id(workspaceId);
      workspaceApi.createWorkspace(requestBody);
    } catch (ApiException apiEx) {
      logger.debug("Caught exception creating workspace ", apiEx);
    }
    int httpCode = workspaceApi.getApiClient().getStatusCode();
    logger.info("CREATE workspace HTTP code: {}", httpCode);
    assertThat(httpCode, equalTo(200));
    // Second, create a data reference in that workspace.
    CreateDataReferenceRequestBody referenceRequest = DataReferenceUtils
        .defaultDataReferenceRequest();
    dataReference = workspaceApi.createDataReference(referenceRequest, workspaceId);
    httpCode = workspaceApi.getApiClient().getStatusCode();
    logger.info("CREATE data reference HTTP code: {}", httpCode);
    assertThat(httpCode, equalTo(200));
  }

  @Override
  public void userJourney(TestUserSpecification testUser) throws Exception {
    ApiClient apiClient = WorkspaceManagerServiceUtils.getClientForTestUser(testUser, server);
    WorkspaceApi workspaceApi = new WorkspaceApi(apiClient);
    DataReferenceDescription receivedReference = workspaceApi.getDataReferenceByName(workspaceId, dataReference.getReferenceType(), dataReference.getName());
    int httpCode = workspaceApi.getApiClient().getStatusCode();
    logger.info("GET workspace HTTP code: {}", httpCode);
    assertThat(httpCode, equalTo(200));
    assertThat(receivedReference, equalTo(dataReference));
  }

  @Override
  public void cleanup(List<TestUserSpecification> testUsers) throws Exception {
    assertThat("There must be at least one test user in configs/testusers directory.", testUsers!=null && testUsers.size()>0);
    ApiClient apiClient = WorkspaceManagerServiceUtils.getClientForTestUser(testUsers.get(0), server);
    WorkspaceApi workspaceApi = new WorkspaceApi(apiClient);

    try {
      workspaceApi.deleteWorkspace(workspaceId);
    } catch (ApiException apiEx) {
      logger.debug("Caught exception deleting workspace ", apiEx);
    }

    int httpCode = workspaceApi.getApiClient().getStatusCode();
    logger.info("DELETE workspace HTTP code: {}", httpCode);
    assertThat(httpCode, equalTo(204));
  }
}
