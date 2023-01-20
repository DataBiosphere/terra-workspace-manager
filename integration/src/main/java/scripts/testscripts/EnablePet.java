package scripts.testscripts;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import bio.terra.common.sam.SamRetry;
import bio.terra.testrunner.runner.config.TestUserSpecification;
import bio.terra.workspace.api.WorkspaceApi;
import bio.terra.workspace.model.IamRole;
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
  private TestUserSpecification secondUser;

  @Override
  protected void doSetup(List<TestUserSpecification> testUsers, WorkspaceApi workspaceApi)
      throws Exception {
    super.doSetup(testUsers, workspaceApi);
    // Note the 0th user is the owner of the workspace, pulled out in the super class.
    assertThat(
        "There must be at least two test users defined for this test.",
        testUsers != null && testUsers.size() > 1);
    secondUser = testUsers.get(1);
    projectId = CloudContextMaker.createGcpCloudContext(getWorkspaceId(), workspaceApi);
  }

  @Override
  protected void doUserJourney(TestUserSpecification testUser, WorkspaceApi userWorkspaceApi)
      throws Exception {
    // Validate that the user cannot impersonate their pet before calling this endpoint.
    GoogleApi samGoogleApi = SamClientUtils.samGoogleApi(testUser, server);
    String petSaEmail = SamRetry.retry(() -> samGoogleApi.getPetServiceAccount(projectId));
    Iam userIamClient = ClientTestUtils.getGcpIamClient(testUser);
    assertFalse(canImpersonateSa(userIamClient, petSaEmail));

    userWorkspaceApi.enablePet(getWorkspaceId());
    assertTrue(canImpersonateSa(userIamClient, petSaEmail));

    // Validate that calling this endpoint as the pet does not grant the pet permission to
    // impersonate itself.
    String rawPetSaToken =
        SamRetry.retry(
            () ->
                samGoogleApi.getPetServiceAccountToken(
                    projectId, ClientTestUtils.TEST_USER_SCOPES));
    AccessToken petSaToken = new AccessToken(rawPetSaToken, null);
    WorkspaceApi petSaWorkspaceApi =
        ClientTestUtils.getWorkspaceClientFromToken(petSaToken, server);

    petSaWorkspaceApi.enablePet(getWorkspaceId());

    // Add second user to the workspace as a reader.
    ClientTestUtils.grantRoleWaitForPropagation(
        userWorkspaceApi, getWorkspaceId(), projectId, secondUser, IamRole.READER);

    // Validate the second user cannot impersonate either user's pet.
    GoogleApi secondUserSamGoogleApi = SamClientUtils.samGoogleApi(secondUser, server);
    String secondUserPetSaEmail =
        SamRetry.retry(() -> secondUserSamGoogleApi.getPetServiceAccount(projectId));
    Iam secondUserIamClient = ClientTestUtils.getGcpIamClient(secondUser);
    assertFalse(canImpersonateSa(secondUserIamClient, secondUserPetSaEmail));
    assertFalse(canImpersonateSa(secondUserIamClient, petSaEmail));

    // Enable the second user to impersonate their pet
    WorkspaceApi secondUserWorkspaceApi = ClientTestUtils.getWorkspaceClient(secondUser, server);
    secondUserWorkspaceApi.enablePet(getWorkspaceId());
    assertTrue(canImpersonateSa(secondUserIamClient, secondUserPetSaEmail));
    // Second user still cannot impersonate first user's pet
    assertFalse(canImpersonateSa(secondUserIamClient, petSaEmail));

    // Remove second user from workspace. This should revoke their permission to impersonate their
    // pet.
    userWorkspaceApi.removeRole(getWorkspaceId(), IamRole.READER, secondUser.userEmail);
    assertTrue(
        ClientTestUtils.getWithRetryOnException(
            () -> assertCannotImpersonateSa(secondUserIamClient, secondUserPetSaEmail)));
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

  private boolean assertCannotImpersonateSa(Iam iamClient, String petSaEmail) throws Exception {
    if (canImpersonateSa(iamClient, petSaEmail)) {
      throw new RuntimeException("User can still impersonate pet SA");
    }
    return true;
  }
}
