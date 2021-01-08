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
    WorkspaceApi workspaceApi = WorkspaceManagerServiceUtils.getWorkspaceApiForTestUser(testUsers.get(0), server);
    // First, create a workspace.
    try {
      CreateWorkspaceRequestBody requestBody = new CreateWorkspaceRequestBody().id(workspaceId);
      workspaceApi.createWorkspace(requestBody);
    } catch (ApiException apiEx) {
      logger.debug("Caught exception creating workspace ", apiEx);
    }
    WorkspaceManagerServiceUtils.assertHttpOk(workspaceApi, "CREATE workspace");
    // Second, create a data reference in that workspace.
    CreateDataReferenceRequestBody referenceRequest = DataReferenceUtils
        .defaultDataReferenceRequest();
    dataReference = workspaceApi.createDataReference(referenceRequest, workspaceId);
    WorkspaceManagerServiceUtils.assertHttpOk(workspaceApi, "CREATE data reference");
  }

  @Override
  public void userJourney(TestUserSpecification testUser) throws Exception {
    WorkspaceApi workspaceApi = WorkspaceManagerServiceUtils.getWorkspaceApiForTestUser(testUser, server);
    DataReferenceDescription receivedReference = workspaceApi.getDataReferenceByName(workspaceId, dataReference.getReferenceType(), dataReference.getName());
    WorkspaceManagerServiceUtils.assertHttpOk(workspaceApi, "GET data reference");
    assertThat(receivedReference, equalTo(dataReference));
  }

  @Override
  public void cleanup(List<TestUserSpecification> testUsers) throws Exception {
    assertThat("There must be at least one test user in configs/testusers directory.", testUsers!=null && testUsers.size()>0);
    WorkspaceApi workspaceApi = WorkspaceManagerServiceUtils.getWorkspaceApiForTestUser(testUsers.get(0), server);
    try {
      workspaceApi.deleteWorkspace(workspaceId);
    } catch (ApiException apiEx) {
      logger.debug("Caught exception deleting workspace ", apiEx);
    }
    WorkspaceManagerServiceUtils.assertHttpOk(workspaceApi, "DELETE workspace");
  }
}
