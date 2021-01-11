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
import scripts.utils.WorkspaceTestScriptBase;

public class GetDataReferenceByName extends WorkspaceTestScriptBase {
  private static final Logger logger = LoggerFactory.getLogger(GetDataReferenceByName.class);
  private UUID workspaceId;
  private DataReferenceDescription dataReference;

  @Override
  public void doSetup(List<TestUserSpecification> testUsers, WorkspaceApi workspaceApi) throws ApiException {
    workspaceId = UUID.randomUUID();
    // First, create a workspace.
    CreateWorkspaceRequestBody requestBody = new CreateWorkspaceRequestBody().id(workspaceId);
    workspaceApi.createWorkspace(requestBody);
    WorkspaceManagerServiceUtils.assertHttpOk(workspaceApi, "CREATE workspace");
    // Second, create a data reference in that workspace.
    CreateDataReferenceRequestBody referenceRequest = DataReferenceUtils
        .defaultDataReferenceRequest();
    dataReference = workspaceApi.createDataReference(referenceRequest, workspaceId);
    WorkspaceManagerServiceUtils.assertHttpOk(workspaceApi, "CREATE data reference");
  }

  @Override
  public void doUserJourney(TestUserSpecification testUser, WorkspaceApi workspaceApi) throws ApiException {
    DataReferenceDescription receivedReference = workspaceApi.getDataReferenceByName(workspaceId, dataReference.getReferenceType(), dataReference.getName());
    WorkspaceManagerServiceUtils.assertHttpOk(workspaceApi, "GET data reference");
    assertThat(receivedReference, equalTo(dataReference));
  }

  @Override
  public void doCleanup(List<TestUserSpecification> testUsers, WorkspaceApi workspaceApi) throws ApiException {
    workspaceApi.deleteWorkspace(workspaceId);
    WorkspaceManagerServiceUtils.assertHttpOk(workspaceApi, "DELETE workspace");
  }
}
