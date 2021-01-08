package scripts.testscripts;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

import bio.terra.testrunner.runner.config.TestUserSpecification;
import bio.terra.workspace.api.WorkspaceApi;
import bio.terra.workspace.client.ApiException;
import bio.terra.workspace.model.CreateWorkspaceRequestBody;
import bio.terra.workspace.model.CreatedWorkspace;
import java.util.List;
import java.util.UUID;
import scripts.utils.WorkspaceTestScriptBase;

public class GetRoles extends WorkspaceTestScriptBase {
private static final IamRole IAM_ROLE = IamRole.WRITER;
  private UUID workspaceId;
  private CreatedWorkspace workspace;

  // Grant a role for all test users on a new workspace
  @Override
  public void doSetup(List<TestUserSpecification> testUsers, WorkspaceApi workspaceApi)
      throws ApiException {
    final CreateWorkspaceRequestBody requestBody = new CreateWorkspaceRequestBody();
    requestBody.setId(workspaceId);
    workspace = workspaceApi.createWorkspace(requestBody);
    for (TestUserSpecification testUser : testUsers) {
      workspaceApi.grantRole(workspaceId, IAM_ROLE, testUser.userEmail);
    }
  }

  @Override
  public void doUserJourney(TestUserSpecification testUser, WorkspaceApi workspaceApi)
      throws ApiException {
   final bio.terra.workspace.model.RoleBindingList roles = workspaceApi.getRoles(workspaceId);
   // our user should be in this list, with the correct role
    final boolean isInList = roles.stream()
        .anyMatch(rb ->
            rb.getRole() == IAM_ROLE &&
            rb.getMembers().contains(testUser.userEmail));
    assertThat(isInList, equalTo(true));
  }

  @Override
  public void doCleanup(List<TestUserSpecification> testUsers, WorkspaceApi workspaceApi)
      throws ApiException {
    // remove role
  }
}
