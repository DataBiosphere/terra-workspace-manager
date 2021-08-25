package scripts.testscripts;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;


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

/**
 * This test exercises WSM's listWorkspaces endpoint. Note that because this test reads the full
 * set of a user's workspaces, it is not thread safe and only one instance of this test should
 * run against a server at once.
 */
public class ListWorkspaces extends WorkspaceAllocateTestScriptBase {

  private UUID workspaceId2;
  private UUID workspaceId3;
  private WorkspaceApi secondUserApi;

  @Override
  protected void doSetup(List<TestUserSpecification> testUsers, WorkspaceApi workspaceApi) throws Exception {
    assertThat("There must be two test users to run this test",
        testUsers.size() == 2);
    // Primary test user (index 0) is handled in the test base.
    TestUserSpecification nonWorkspaceUser = testUsers.get(1);
    secondUserApi = ClientTestUtils.getWorkspaceClient(nonWorkspaceUser, server);
    // We expect test users to never have permanent workspaces. However, they may have some
    // workspaces due to previous test failures. Before running this test, we clean up all
    // workspaces for both test users.
    WorkspaceDescriptionList preTestWorkspaceList = workspaceApi.listWorkspaces(/*offset=*/0, /*limit=*/10000);
    for (WorkspaceDescription workspace : preTestWorkspaceList.getWorkspaces()) {
      workspaceApi.deleteWorkspace(workspace.getId());
    }
    WorkspaceDescriptionList secondUserPreTestWorkspaceList = secondUserApi.listWorkspaces(/*offset=*/0, /*limit=*/10000);
    for (WorkspaceDescription workspace : secondUserPreTestWorkspaceList.getWorkspaces()) {
      secondUserApi.deleteWorkspace(workspace.getId());
    }

    // Set up the test fixture workspaces. This must happen after cleaning up a user's workspaces.
    super.doSetup(testUsers, workspaceApi);
    workspaceId2 = UUID.randomUUID();
    createWorkspace(workspaceId2, getSpendProfileId(), workspaceApi);
    workspaceId3 = UUID.randomUUID();
    createWorkspace(workspaceId3, getSpendProfileId(), workspaceApi);
  }

  @Override
  protected void doCleanup(List<TestUserSpecification> testUsers, WorkspaceApi workspaceApi)
      throws Exception {
    super.doCleanup(testUsers, workspaceApi);
    workspaceApi.deleteWorkspace(workspaceId2);
    workspaceApi.deleteWorkspace(workspaceId3);
  }

  @Override
  protected void doUserJourney(TestUserSpecification testUser, WorkspaceApi workspaceApi)
      throws Exception {
    // First, check that listing 3 workspaces will return the 3 we created.
    WorkspaceDescriptionList workspaceList = workspaceApi.listWorkspaces(/*offset=*/0, /*limit=*/3);
    List<UUID> workspaceIdList = workspaceList.getWorkspaces().stream().map(WorkspaceDescription::getId).collect(
        Collectors.toList());
    assertThat(workspaceIdList, containsInAnyOrder(equalTo(getWorkspaceId()), equalTo(workspaceId2), equalTo(workspaceId3)));

    // Next, call the endpoint three times with limit 1 and different offsets.
    List<WorkspaceDescription> callResults = new ArrayList<>();
    callResults.addAll(workspaceApi.listWorkspaces(/*offset=*/0, /*limit=*/1).getWorkspaces());
    callResults.addAll(workspaceApi.listWorkspaces(/*offset=*/1, /*limit=*/1).getWorkspaces());
    callResults.addAll(workspaceApi.listWorkspaces(/*offset=*/2, /*limit=*/1).getWorkspaces());
    List<UUID> callResultIdList = workspaceList.getWorkspaces().stream().map(WorkspaceDescription::getId).collect(
        Collectors.toList());
    assertThat(callResultIdList, containsInAnyOrder(equalTo(getWorkspaceId()), equalTo(workspaceId2), equalTo(workspaceId3)));

    // Assert that even with a limit > 3, we still only see the workspaces created here.
    WorkspaceDescriptionList largeLimitResult = workspaceApi.listWorkspaces(/*offset=*/0, /*limit=*/100);
    assertEquals(3, largeLimitResult.getWorkspaces().size());

    // Validate that a different user will not see any of these workspaces.
    WorkspaceDescriptionList secondUserResult = secondUserApi.listWorkspaces(0, 3);
    assertEquals(0, secondUserResult.getWorkspaces().size());
  }

}
