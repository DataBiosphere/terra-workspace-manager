package scripts.testscripts;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

  @Override
  protected void doSetup(List<TestUserSpecification> testUsers, WorkspaceApi workspaceApi)
      throws Exception {
    super.doSetup(testUsers, workspaceApi);
    projectId = CloudContextMaker.createGcpCloudContext(getWorkspaceId(), workspaceApi);
  }

  @Override
  protected void doUserJourney(TestUserSpecification testUser, WorkspaceApi userWorkspaceApi)
      throws Exception {
    // Validate that the user cannot impersonate their pet before calling this endpoint.
    GoogleApi samGoogleApi = SamClientUtils.samGoogleApi(testUser, server);
    String petSaEmail = samGoogleApi.getPetServiceAccount(projectId);
    Iam userIamClient = ClientTestUtils.getGcpIamClient(testUser);
    assertFalse(canImpersonateSa(userIamClient, petSaEmail));

    String returnedPetSaEmail = userWorkspaceApi.enablePet(getWorkspaceId());
    assertEquals(petSaEmail, returnedPetSaEmail);
    assertTrue(canImpersonateSa(userIamClient, petSaEmail));

    // Validate that calling this endpoint as the pet does not grant the pet permission to
    // impersonate itself.
    String rawPetSaToken =
        samGoogleApi.getPetServiceAccountToken(projectId, ClientTestUtils.TEST_USER_SCOPES);
    AccessToken petSaToken = new AccessToken(rawPetSaToken, null);
    WorkspaceApi petSaWorkspaceApi =
        ClientTestUtils.getWorkspaceClientFromToken(petSaToken, server);
    String petEnableResult = petSaWorkspaceApi.enablePet(getWorkspaceId());
    assertEquals(petSaEmail, petEnableResult);
    Iam petIamClient = ClientTestUtils.getGcpIamClientFromToken(petSaToken);
    // TODO(PF-991): This will fail until pet SA self-impersonation is fixed.
    // assertFalse(canImpersonateSa(petIamClient, petSaEmail));
  }

  private boolean canImpersonateSa(Iam iamClient, String petSaEmail) throws Exception {
    String fullyQualifiedSaName =
        String.format("projects/%s/serviceAccounts/%s", projectId, petSaEmail);
    TestIamPermissionsRequest testIamRequest =
        new TestIamPermissionsRequest()
            .setPermissions(Collections.singletonList("iam.serviceAccounts.actAs"));
    TestIamPermissionsResponse response =
        iamClient
            .projects()
            .serviceAccounts()
            .testIamPermissions(fullyQualifiedSaName, testIamRequest)
            .execute();
    // When no permissions are active, the permissions field of the response is null instead of an
    // empty list. This is a quirk of the GCP client library.
    return response.getPermissions() != null
        && response.getPermissions().contains("iam.serviceAccounts.actAs");
  }
}
