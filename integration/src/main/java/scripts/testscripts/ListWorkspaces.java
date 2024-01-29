package scripts.testscripts;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;

import bio.terra.testrunner.runner.config.TestUserSpecification;
import bio.terra.workspace.api.WorkspaceApi;
import bio.terra.workspace.model.IamRole;
import bio.terra.workspace.model.WorkspaceDescription;
import bio.terra.workspace.model.WorkspaceDescriptionList;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import scripts.utils.ClientTestUtils;
import scripts.utils.WorkspaceAllocateTestScriptBase;

public class ListWorkspaces extends WorkspaceAllocateTestScriptBase {

  private UUID workspaceId2;
  private WorkspaceApi secondUserApi;

  @Override
  protected void doSetup(List<TestUserSpecification> testUsers, WorkspaceApi firstUserApi)
      throws Exception {
    super.doSetup(testUsers, firstUserApi);
    assertThat("There must be two test users to run this test", testUsers.size() == 2);
    // Set up second test user. (First test user was set up in base class.)
    TestUserSpecification secondUser = testUsers.get(1);
    secondUserApi = ClientTestUtils.getWorkspaceClient(secondUser, server);

    // Have second user create second workspace. (First workspace was set up in base class.)
    workspaceId2 = UUID.randomUUID();
    createWorkspace(workspaceId2, getSpendProfileId(), secondUserApi);
    // Add first user as workspace reader
    ClientTestUtils.grantRole(secondUserApi, workspaceId2, testUsers.get(0), IamRole.READER);
  }

  @Override
  protected void doCleanup(List<TestUserSpecification> testUsers, WorkspaceApi workspaceApi)
      throws Exception {
    super.doCleanup(testUsers, workspaceApi);
    secondUserApi.deleteWorkspace(workspaceId2);
  }

  @Override
  protected void doUserJourney(TestUserSpecification testUser, WorkspaceApi firstUserApi)
      throws Exception {
    // For the sake of listing "all" of a user's workspaces we must have a maximum upper limit. Test
    // users are expected to never have permanent workspaces, so this should be more than
    // sufficient.
    int MAX_USER_WORKSPACES = 10000;

    // While test users should not have any permanent workspaces, they may have some entries from
    // previous failures or other tests running in parallel. We therefore cannot assume they only
    // have 2 workspaces.
    List<WorkspaceDescription> workspaces =
        firstUserApi
            .listWorkspaces(
                /* offset= */ 0,
                /* limit= */ MAX_USER_WORKSPACES,
                /* minimumHighestRole= */ IamRole.READER)
            .getWorkspaces();
    // Assert both workspaces are returned.
    List<UUID> workspaceIds =
        workspaces.stream().map(WorkspaceDescription::getId).collect(Collectors.toList());
    assertThat(workspaceIds, hasItems(getWorkspaceId(), workspaceId2));
    // Assert first user is owner on first workspace, and reader on second workspace
    workspaces.forEach(
        workspace -> {
          if (workspace.getId() == getWorkspaceId()) {
            assertEquals(IamRole.OWNER, workspace.getHighestRole());
          } else if (workspace.getId() == workspaceId2) {
            assertEquals(IamRole.READER, workspace.getHighestRole());
          }
        });

    // Next, cover the same set of workspaces across 3 pages.
    int pageSize = MAX_USER_WORKSPACES / 3;
    List<WorkspaceDescription> callResults = new ArrayList<>();
    callResults.addAll(
        firstUserApi
            .listWorkspaces(
                /* offset= */ 0, /* limit= */ pageSize, /* minimumHighestRole= */ IamRole.READER)
            .getWorkspaces());
    callResults.addAll(
        firstUserApi
            .listWorkspaces(
                /* offset= */ pageSize,
                /* limit= */ pageSize,
                /* minimumHighestRole= */ IamRole.READER)
            .getWorkspaces());
    // pageSize may not be divisible by 3, so cover all remaining workspaces here instead.
    callResults.addAll(
        firstUserApi
            .listWorkspaces(
                /* offset= */ pageSize * 2,
                /* limit= */ (MAX_USER_WORKSPACES - 2 * pageSize),
                /* minimumHighestRole= */ IamRole.READER)
            .getWorkspaces());
    List<UUID> callResultIdList =
        callResults.stream().map(WorkspaceDescription::getId).collect(Collectors.toList());
    assertThat(callResultIdList, hasItems(getWorkspaceId(), workspaceId2));

    // Validate second user only sees second workspace.
    WorkspaceDescriptionList secondUserResult =
        secondUserApi.listWorkspaces(
            0, MAX_USER_WORKSPACES, /* minimumHighestRole= */ IamRole.READER);
    List<UUID> secondCallResults =
        secondUserResult.getWorkspaces().stream()
            .map(WorkspaceDescription::getId)
            .collect(Collectors.toList());
    assertThat(secondCallResults, not(hasItem(getWorkspaceId())));
    assertThat(secondCallResults, hasItem(workspaceId2));
  }
}
