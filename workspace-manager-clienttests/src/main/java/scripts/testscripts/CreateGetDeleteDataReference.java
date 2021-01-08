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
    // This method relies on a persistent snapshot existing in Data Repo, currently in dev.
    // This snapshot was created using our dev service account, which is a steward in dev Data Repo.
    // First, I created a dataset "wm_integration_test_dataset" using TDR's
    // snapshot-test-dataset.json. Then, I created the snapshot
    // "workspace_integration_test_snapshot" using the "byFullView" mode. Finally, I added the
    // integration test user as a reader of this snapshot.
    // These steps should only need to be repeated if the dev DataRepo data is deleted, or to
    // support this test in other DataRepo environments.
    // Data Repo makes a reasonable effort to maintain their dev environment, so this should be a
    // very rare occurrence.
    DataRepoSnapshot snapshotReference =
        new DataRepoSnapshot()
            .snapshot("97b5559a-2f8f-4df3-89ae-5a249173ee0c")
            .instanceName("terra");
    String dataReferenceName = "workspace_integration_test_snapshot";
    CreateDataReferenceRequestBody referenceRequest =
        new CreateDataReferenceRequestBody()
            .name(dataReferenceName)
            .referenceType(ReferenceTypeEnum.DATA_REPO_SNAPSHOT)
            .reference(snapshotReference)
            .cloningInstructions(CloningInstructionsEnum.NOTHING);
    DataReferenceDescription createdReferenceDescription = workspaceApi.createDataReference(referenceRequest, id);
    int httpCode = workspaceApi.getApiClient().getStatusCode();
    logger.info("CREATE data reference HTTP code: {}", httpCode);
    assertThat(httpCode, equalTo(200));

    DataReferenceDescription getResult = workspaceApi.getDataReference(createdReferenceDescription.getReferenceId(), id);
    httpCode = workspaceApi.getApiClient().getStatusCode();
    logger.info("GET data reference HTTP code: {}", httpCode);
    assertThat(httpCode, equalTo(200));
    assertThat(getResult, equalTo(createdReferenceDescription));

    workspaceApi.deleteDataReference(getResult.getReferenceId(), id);
    httpCode = workspaceApi.getApiClient().getStatusCode();
    logger.info("DELETE data reference HTTP code: {}", httpCode);
    assertThat(httpCode, equalTo(204));
  }

  @Override
  public void cleanup(List<TestUserSpecification> testUsers) throws Exception {
    assertThat("There must be at least one test user in configs/testusers directory.", testUsers!=null && testUsers.size()>0);
  }
}
