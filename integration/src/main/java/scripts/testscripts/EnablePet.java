package scripts.testscripts;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import bio.terra.testrunner.runner.config.TestUserSpecification;
import bio.terra.workspace.api.WorkspaceApi;
import com.google.api.services.iam.v1.Iam;
import com.google.api.services.iam.v1.model.TestIamPermissionsRequest;
import com.google.api.services.iam.v1.model.TestIamPermissionsResponse;
import com.google.auth.oauth2.AccessToken;
import java.util.Collections;
import java.util.List;
import org.broadinstitute.dsde.workbench.client.sam.api.GoogleApi;
import scripts.utils.ClientTestUtils;
import scripts.utils.CloudContextMaker;
import scripts.utils.SamClientUtils;
import scripts.utils.WorkspaceAllocateTestScriptBase;

public class EnablePet extends WorkspaceAllocateTestScriptBase {

  private String projectId;
  private String samUri;

  @Override
  protected void doSetup(List<TestUserSpecification> testUsers, WorkspaceApi workspaceApi) throws Exception {
    super.doSetup(testUsers, workspaceApi);
    projectId = CloudContextMaker.createGcpCloudContext(getWorkspaceId(), workspaceApi);
  }

  @Override
  public void setParameters(List<String> parameters) throws Exception {
    super.setParameters(parameters);

    if (parameters == null || parameters.size() < 2) {
      throw new IllegalArgumentException(
          "Must provide Sam URI as second parameter to this test");
    } else {
      samUri = parameters.get(1);
    }
  }

  @Override
  protected void doUserJourney(TestUserSpecification testUser, WorkspaceApi userWorkspaceApi)
      throws Exception {
    // Validate that the user cannot impersonate their pet before calling this endpoint.
    GoogleApi samGoogleApi = SamClientUtils.samGoogleApi(testUser, samUri);
    String petSaEmail = samGoogleApi.getPetServiceAccount(projectId);
    Iam userIamClient = ClientTestUtils.getGcpIamClient(testUser);
    // TODO(PF-765): This will fail until project level SA permissions are removed, as the user
    //   gets this permission at the project level.
    // assertFalse(userCanImpersonateSa(userIamClient, petSaEmail));

    String returnedPetSaEmail = userWorkspaceApi.enablePet(getWorkspaceId());
    assertEquals(petSaEmail, returnedPetSaEmail);
    assertTrue(userCanImpersonateSa(userIamClient, petSaEmail));

    // Validate that calling this endpoint as the pet does not grant the pet permission to
    // impersonate itself.
    String rawPetSaToken = samGoogleApi.getPetServiceAccountToken(projectId, ClientTestUtils.TEST_USER_SCOPES);
    AccessToken petSaToken = new AccessToken(rawPetSaToken, null);
    WorkspaceApi petSaWorkspaceApi = ClientTestUtils.getWorkspaceClientFromToken(petSaToken, server);
    String petEnableResult = petSaWorkspaceApi.enablePet(getWorkspaceId());
    assertEquals(petSaEmail, petEnableResult);
    Iam petIamClient = ClientTestUtils.getGcpIamClientFromToken(petSaToken);
    // TODO(PF-765): This will fail until project level SA permissions are removed, as the pet SA
    //   gets this permission at the project level.
    // assertFalse(userCanImpersonateSa(petIamClient, petSaEmail));
  }

  private boolean userCanImpersonateSa(Iam iamClient, String petSaEmail) throws Exception {
    String fullyQualifiedSaName = String.format("projects/%s/serviceAccounts/%s", projectId, petSaEmail);
    TestIamPermissionsRequest testIamRequest = new TestIamPermissionsRequest()
        .setPermissions(Collections.singletonList("iam.serviceAccounts.actAs"));
    TestIamPermissionsResponse response = iamClient.projects().serviceAccounts()
        .testIamPermissions(fullyQualifiedSaName, testIamRequest).execute();
    // When no permissions are active, the permissions field of the response is null instead of an
    // empty list. This is a quirk of the GCP client library.
    return response.getPermissions() != null && response.getPermissions().contains("iam.serviceAccounts.actAs");
  }
}
