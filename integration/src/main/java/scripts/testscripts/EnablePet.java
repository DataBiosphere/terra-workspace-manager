package scripts.testscripts;

import static org.junit.jupiter.api.Assertions.assertEquals;

import bio.terra.testrunner.runner.config.TestUserSpecification;
import bio.terra.workspace.api.WorkspaceApi;
import com.google.api.services.iam.v1.Iam;
import com.google.api.services.iam.v1.model.TestIamPermissionsRequest;
import com.google.api.services.iam.v1.model.TestIamPermissionsResponse;
import java.util.Collections;
import java.util.List;
import scripts.utils.ClientTestUtils;
import scripts.utils.CloudContextMaker;
import scripts.utils.WorkspaceAllocateTestScriptBase;

// TODO(PF-765): This is not an effective test until project level SA permissions are removed.
// Validate that this continues to pass after they are.
public class EnablePet extends WorkspaceAllocateTestScriptBase {

  private String projectId;

  @Override
  protected void doSetup(List<TestUserSpecification> testUsers, WorkspaceApi workspaceApi) throws Exception {
    super.doSetup(testUsers, workspaceApi);
    projectId = CloudContextMaker.createGcpCloudContext(getWorkspaceId(), workspaceApi);
  }

  @Override
  protected void doUserJourney(TestUserSpecification testUser, WorkspaceApi workspaceApi)
      throws Exception {
    String petSaEmail = workspaceApi.enablePet(getWorkspaceId());

    String fullyQualifiedSaName = String.format("projects/%s/serviceAccounts/%s", projectId, petSaEmail);
    Iam iamClient = ClientTestUtils.getGcpIamClient(testUser);
    TestIamPermissionsRequest testIamRequest = new TestIamPermissionsRequest()
        .setPermissions(Collections.singletonList("iam.serviceAccounts.actAs"));
    TestIamPermissionsResponse response = iamClient.projects().serviceAccounts()
        .testIamPermissions(fullyQualifiedSaName, testIamRequest).execute();
    assertEquals(1, response.getPermissions().size());
    assertEquals("iam.serviceAccounts.actAs", response.getPermissions().get(0));
  }
}
