package scripts.testscripts;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.not;


import bio.terra.testrunner.runner.config.TestUserSpecification;
import bio.terra.workspace.api.WorkspaceApi;
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
  private UUID workspaceId3;
  private WorkspaceApi secondUserApi;

  @Override
  protected void doSetup(List<TestUserSpecification> testUsers, WorkspaceApi firstUserApi) throws Exception {
    super.doSetup(testUsers, firstUserApi);
    assertThat("There must be two test users to run this test",
        testUsers.size() == 2);
    // Primary test user (index 0) is handled in the test base.
    TestUserSpecification nonWorkspaceUser = testUsers.get(1);
    secondUserApi = ClientTestUtils.getWorkspaceClient(nonWorkspaceUser, server);

    // Set up the test fixture workspaces. This must happen after cleaning up a user's workspaces.
    // First workspace is set up via test base.
    workspaceId2 = UUID.randomUUID();
    createWorkspace(workspaceId2, getSpendProfileId(), firstUserApi);
    workspaceId3 = UUID.randomUUID();
    createWorkspace(workspaceId3, getSpendProfileId(), firstUserApi);
  }

  @Override
  protected void doCleanup(List<TestUserSpecification> testUsers, WorkspaceApi workspaceApi)
      throws Exception {
    super.doCleanup(testUsers, workspaceApi);
    workspaceApi.deleteWorkspace(workspaceId2);
    workspaceApi.deleteWorkspace(workspaceId3);
  }

  @Override
  protected void doUserJourney(TestUserSpecification testUser, WorkspaceApi firstUserApi)
      throws Exception {
    // For the sake of listing "all" of a user's workspaces we must have a maximum upper limit. Test
    // users are expected to never have permanent workspaces, so this should be more than sufficient.
    int MAX_USER_WORKSPACES = 10000;

    // While test users should not have any permanent workspaces, they may have some entries from
    // previous failures or other tests running in parallel. We therefore cannot assume they only
    // have 3 workspaces.
    WorkspaceDescriptionList workspaceList = firstUserApi.listWorkspaces(/*offset=*/0, /*limit=*/
        MAX_USER_WORKSPACES);
    List<UUID> workspaceIdList = workspaceList.getWorkspaces().stream().map(WorkspaceDescription::getId).collect(
        Collectors.toList());
    assertThat(workspaceIdList, hasItems(getWorkspaceId(), workspaceId2, workspaceId3));

    // Next, cover the same set of workspaces across 3 pages.
    int pageSize = MAX_USER_WORKSPACES / 3;
    List<WorkspaceDescription> callResults = new ArrayList<>();
    callResults.addAll(firstUserApi.listWorkspaces(/*offset=*/0, /*limit=*/pageSize).getWorkspaces());
    callResults.addAll(firstUserApi.listWorkspaces(/*offset=*/pageSize, /*limit=*/pageSize).getWorkspaces());
    // pageSize may not be divisible by 3, so cover all remaining workspaces here instead.
    callResults.addAll(firstUserApi.listWorkspaces(/*offset=*/pageSize*2, /*limit=*/(
        MAX_USER_WORKSPACES - 2*pageSize)).getWorkspaces());
    List<UUID> callResultIdList = workspaceList.getWorkspaces().stream().map(WorkspaceDescription::getId).collect(
        Collectors.toList());
    assertThat(callResultIdList, hasItems(getWorkspaceId(), workspaceId2, workspaceId3));

    // Validate that a different user will not see any of these workspaces.
    WorkspaceDescriptionList secondUserResult = secondUserApi.listWorkspaces(0, MAX_USER_WORKSPACES);
    List<UUID> secondCallResultList = secondUserResult.getWorkspaces().stream().map(WorkspaceDescription::getId)
        .collect(Collectors.toList());
    assertThat(secondCallResultList, not(hasItem(getWorkspaceId())));
    assertThat(secondCallResultList, not(hasItem(workspaceId2)));
    assertThat(secondCallResultList, not(hasItem(workspaceId3)));
  }

}
