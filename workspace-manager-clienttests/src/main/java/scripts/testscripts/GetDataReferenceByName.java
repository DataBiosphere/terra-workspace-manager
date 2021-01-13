package scripts.testscripts;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

import bio.terra.testrunner.runner.config.TestUserSpecification;
import bio.terra.workspace.api.WorkspaceApi;
import bio.terra.workspace.client.ApiException;
import bio.terra.workspace.model.CreateDataReferenceRequestBody;
import bio.terra.workspace.model.DataReferenceDescription;
import java.util.List;
import scripts.utils.ClientTestUtils;
import scripts.utils.WorkspaceFixtureTestScriptBase;

public class GetDataReferenceByName extends WorkspaceFixtureTestScriptBase {
  private DataReferenceDescription dataReference;

  @Override
  public void doSetup(List<TestUserSpecification> testUsers, WorkspaceApi workspaceApi) throws ApiException {
    // Create the workspace test fixture.
    super.doSetup(testUsers, workspaceApi);
    // Create a data reference in the provided workspace.
    CreateDataReferenceRequestBody referenceRequest = ClientTestUtils.getTestCreateDataReferenceRequestBody();
    dataReference = workspaceApi.createDataReference(referenceRequest, getWorkspaceId());
    ClientTestUtils.assertHttpSuccess(workspaceApi, "CREATE data reference");
  }

  @Override
  public void doUserJourney(TestUserSpecification testUser, WorkspaceApi workspaceApi) throws ApiException {
    DataReferenceDescription receivedReference = workspaceApi.getDataReferenceByName(getWorkspaceId(), dataReference.getReferenceType(), dataReference.getName());
    ClientTestUtils.assertHttpSuccess(workspaceApi, "GET data reference");
    assertThat(receivedReference, equalTo(dataReference));
  }
}
