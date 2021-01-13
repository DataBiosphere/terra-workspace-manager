package scripts.testscripts;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

import bio.terra.testrunner.runner.config.TestUserSpecification;
import bio.terra.workspace.api.WorkspaceApi;
import bio.terra.workspace.client.ApiException;
import bio.terra.workspace.model.IamRole;
import bio.terra.workspace.model.RoleBindingList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scripts.utils.ClientTestUtils;
import scripts.utils.WorkspaceFixtureTestScriptBase;

public class RoleLifecycle extends WorkspaceFixtureTestScriptBase {
private static final IamRole IAM_ROLE = IamRole.WRITER;
  private static final Logger logger = LoggerFactory.getLogger(RoleLifecycle.class);

  @Override
  public void doSetup(List<TestUserSpecification> testUsers, WorkspaceApi workspaceApi)
      throws ApiException {
    super.doSetup(testUsers, workspaceApi);

    for (TestUserSpecification testUser : testUsers) {
      logger.info("Granting role {} for user {} on workspace id {}", IAM_ROLE.toString(),
          testUser.userEmail, getWorkspaceId().toString());
      // grant the role
      workspaceApi.grantRole(getWorkspaceId(), IAM_ROLE, testUser.userEmail);
    }
  }

  @Override
  public void doUserJourney(TestUserSpecification testUser, WorkspaceApi workspaceApi)
      throws ApiException {

    // check granted roles
    final RoleBindingList roles = workspaceApi.getRoles(getWorkspaceId());

    logger.debug("For workspace id {}, retrieved role bindings:\n{}", getWorkspaceId().toString(), roles.toString());
   // our user should be in this list, with the correct role
    boolean isInList = ClientTestUtils.containsBinding(roles, testUser.userEmail, IAM_ROLE);
    assertThat(isInList, equalTo(true));
  }

}
