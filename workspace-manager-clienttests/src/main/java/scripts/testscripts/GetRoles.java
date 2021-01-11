package scripts.testscripts;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

import bio.terra.testrunner.runner.config.TestUserSpecification;
import bio.terra.workspace.api.WorkspaceApi;
import bio.terra.workspace.client.ApiException;
import bio.terra.workspace.model.CreateWorkspaceRequestBody;
import bio.terra.workspace.model.CreatedWorkspace;
import bio.terra.workspace.model.IamRole;
import bio.terra.workspace.model.RoleBinding;
import bio.terra.workspace.model.RoleBindingList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scripts.utils.WorkspaceTestScriptBase;

public class GetRoles extends WorkspaceTestScriptBase {
private static final IamRole IAM_ROLE = IamRole.WRITER;
  private static final Logger logger = LoggerFactory.getLogger(GetRoles.class);
  private UUID workspaceId;

  // Grant a role for all test users on a new workspace
  @Override
  public void doSetup(List<TestUserSpecification> testUsers, WorkspaceApi workspaceApi)
      throws ApiException {
    workspaceId = UUID.randomUUID();
    final CreateWorkspaceRequestBody requestBody = new CreateWorkspaceRequestBody();
    requestBody.setId(workspaceId);
    final CreatedWorkspace workspace = workspaceApi.createWorkspace(requestBody);
    assertThat(workspace.getId(), equalTo(workspaceId));
  }

  @Override
  public void doUserJourney(TestUserSpecification testUser, WorkspaceApi workspaceApi)
      throws ApiException {
    logger.info("Granting role {} for user {} on workspace id {}", IAM_ROLE.toString(), testUser.userEmail, workspaceId.toString());
    // grant the role
    workspaceApi.grantRole(workspaceId, IAM_ROLE, testUser.userEmail);

    // check granted roles
    final RoleBindingList roles = workspaceApi.getRoles(workspaceId);

    logger.debug("For workspace id {}, retrieved role bindings:\n{}", workspaceId.toString(), roles.toString());
   // our user should be in this list, with the correct role
    boolean isInList = roles.stream()
        .anyMatch(rb ->
            rb.getRole() == IAM_ROLE &&
            rb.getMembers().contains(testUser.userEmail));
    assertThat(isInList, equalTo(true));

    // remove the role
    workspaceApi.removeRole(workspaceId, IAM_ROLE, testUser.userEmail);
    final RoleBindingList updatedRoles = workspaceApi.getRoles(workspaceId);
    isInList = roles.stream()
        .anyMatch(rb ->
            rb.getRole() == IAM_ROLE &&
                rb.getMembers().contains(testUser.userEmail));
    assertThat(isInList, equalTo(false));
  }

  @Override
  public void doCleanup(List<TestUserSpecification> testUsers, WorkspaceApi workspaceApi)
      throws ApiException {
    // assert that no users have the iam role
    workspaceApi.deleteWorkspace(workspaceId);
  }
}
