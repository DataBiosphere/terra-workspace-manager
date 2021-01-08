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
  private UUID workspaceId;

  @Override
  public void setup(List<TestUserSpecification> testUsers) throws Exception {
    assertThat("There must be at least one test user in configs/testusers directory.", testUsers!=null && testUsers.size()>0);
    workspaceId = UUID.randomUUID();
    WorkspaceApi workspaceApi = WorkspaceManagerServiceUtils.getWorkspaceApiForTestUser(testUsers.get(0), server);
    try {
      CreateWorkspaceRequestBody requestBody = new CreateWorkspaceRequestBody().id(workspaceId);
      workspaceApi.createWorkspace(requestBody);
    } catch (ApiException apiEx) {
      logger.debug("Caught exception creating workspace ", apiEx);
    }

    WorkspaceManagerServiceUtils.assertHttpOk(workspaceApi, "CREATE workspace");
  }

  @Override
  public void userJourney(TestUserSpecification testUser) throws Exception {
    WorkspaceApi workspaceApi = WorkspaceManagerServiceUtils.getWorkspaceApiForTestUser(testUser, server);
    CreateDataReferenceRequestBody referenceRequest = DataReferenceUtils
        .defaultDataReferenceRequest();
    DataReferenceDescription createdReferenceDescription = workspaceApi.createDataReference(referenceRequest, workspaceId);
    WorkspaceManagerServiceUtils.assertHttpOk(workspaceApi, "CREATE data reference");

    DataReferenceDescription getResult = workspaceApi.getDataReference(workspaceId, createdReferenceDescription.getReferenceId());
    WorkspaceManagerServiceUtils.assertHttpOk(workspaceApi, "GET data reference");
    assertThat(getResult, equalTo(createdReferenceDescription));

    workspaceApi.deleteDataReference(workspaceId, getResult.getReferenceId());
    WorkspaceManagerServiceUtils.assertHttpOk(workspaceApi, "DELETE data reference");
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
