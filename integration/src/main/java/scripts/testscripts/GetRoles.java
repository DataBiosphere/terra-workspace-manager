package scripts.testscripts;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

import bio.terra.testrunner.runner.config.TestUserSpecification;
import bio.terra.workspace.api.WorkspaceApi;
import bio.terra.workspace.client.ApiException;
import bio.terra.workspace.model.IamRole;
import bio.terra.workspace.model.RoleBindingList;
import bio.terra.workspace.model.WorkspaceStageModel;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scripts.utils.ClientTestUtils;
import scripts.utils.WorkspaceAllocateTestScriptBase;

public class GetRoles extends WorkspaceAllocateTestScriptBase {
  private static final IamRole IAM_ROLE = IamRole.WRITER;
  private static final Logger logger = LoggerFactory.getLogger(GetRoles.class);

  @Override
  public void doSetup(List<TestUserSpecification> testUsers, WorkspaceApi workspaceApi)
      throws Exception {
    super.doSetup(testUsers, workspaceApi);

    for (TestUserSpecification testUser : testUsers) {
      ClientTestUtils.grantRole(workspaceApi, getWorkspaceId(), testUser, IAM_ROLE);
    }
  }

  @Override
  public void doUserJourney(TestUserSpecification testUser, WorkspaceApi workspaceApi)
      throws ApiException {

    // check granted roles
    final RoleBindingList roles = workspaceApi.getRoles(getWorkspaceId());

    logger.debug(
        "For workspace id {}, retrieved role bindings:\n{}",
        getWorkspaceId().toString(),
        roles.toString());
    // our user should be in this list, with the correct role
    boolean isInList = ClientTestUtils.containsBinding(roles, testUser.userEmail, IAM_ROLE);
    assertThat(isInList, equalTo(true));
  }

  /**
   * Specify an MC Terra workspace so that roles are supported.
   *
   * @return WorkspaceStageModel
   */
  @Override
  protected WorkspaceStageModel getStageModel() {
    return WorkspaceStageModel.MC_WORKSPACE;
  }
}
