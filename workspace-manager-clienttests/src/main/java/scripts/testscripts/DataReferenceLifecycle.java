package scripts.testscripts;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

import bio.terra.testrunner.runner.config.TestUserSpecification;
import bio.terra.workspace.api.WorkspaceApi;
import bio.terra.workspace.client.ApiException;
import bio.terra.workspace.model.CloningInstructionsEnum;
import bio.terra.workspace.model.CreateDataReferenceRequestBody;
import bio.terra.workspace.model.DataReferenceDescription;
import bio.terra.workspace.model.ReferenceTypeEnum;
import scripts.utils.WorkspaceFixtureTestScriptBase;
import scripts.utils.ClientTestUtils;

public class DataReferenceLifecycle extends WorkspaceFixtureTestScriptBase {

  @Override
  public void doUserJourney(TestUserSpecification testUser, WorkspaceApi workspaceApi)
      throws ApiException {
    // Create a data reference
    final CreateDataReferenceRequestBody body = ClientTestUtils.getTestCreateDataReferenceRequestBody();
    final String referenceName = body.getName();

    final DataReferenceDescription newReference = workspaceApi.createDataReference(body, getWorkspaceId());
    assertDataReferenceDescription(newReference, referenceName);

    // retrieve the data reference
   final DataReferenceDescription retrievedById = workspaceApi.getDataReference(
       getWorkspaceId(), newReference.getReferenceId());
    assertDataReferenceDescription(retrievedById, referenceName);

    // retrieve by name
    final DataReferenceDescription retrievedByName = workspaceApi.getDataReferenceByName(
        getWorkspaceId(), ReferenceTypeEnum.DATA_REPO_SNAPSHOT, referenceName);
    assertDataReferenceDescription(retrievedByName, referenceName);

    // remove reference
    workspaceApi.deleteDataReference(getWorkspaceId(), newReference.getReferenceId());

    // TODO: assert 404 when retrieving again. This may interfere with parent class checking of status.
  }

  private void assertDataReferenceDescription(DataReferenceDescription dataReferenceDescription, String dataReferenceName) {
    assertThat(dataReferenceDescription.getCloningInstructions(), equalTo(CloningInstructionsEnum.REFERENCE));
    assertThat(dataReferenceDescription.getName(), equalTo(dataReferenceName));
    assertThat(dataReferenceDescription.getReferenceType(), equalTo(ReferenceTypeEnum.DATA_REPO_SNAPSHOT));
    assertThat(dataReferenceDescription.getReference().getSnapshot(), equalTo(
        ClientTestUtils.TEST_SNAPSHOT));
    assertThat(dataReferenceDescription.getReference().getInstanceName(), equalTo(
        ClientTestUtils.TERRA_DATA_REPO_INSTANCE));
  }
}
