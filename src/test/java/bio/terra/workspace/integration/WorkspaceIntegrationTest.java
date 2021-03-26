package bio.terra.workspace.integration;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import bio.terra.workspace.common.BaseIntegrationTest;
import bio.terra.workspace.integration.common.auth.AuthService;
import bio.terra.workspace.integration.common.configuration.IntegrationTestConfiguration;
import bio.terra.workspace.integration.common.response.WorkspaceResponse;
import bio.terra.workspace.integration.common.utils.TestUtils;
import bio.terra.workspace.integration.common.utils.WorkspaceManagerTestClient;
import bio.terra.workspace.model.CloningInstructionsEnum;
import bio.terra.workspace.model.CreateDataReferenceRequestBody;
import bio.terra.workspace.model.CreateWorkspaceRequestBody;
import bio.terra.workspace.model.CreatedWorkspace;
import bio.terra.workspace.model.DataReferenceDescription;
import bio.terra.workspace.model.DataReferenceList;
import bio.terra.workspace.model.DataRepoSnapshot;
import bio.terra.workspace.model.ReferenceTypeEnum;
import bio.terra.workspace.model.WorkspaceDescription;
import bio.terra.workspace.model.WorkspaceStageModel;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;

class WorkspaceIntegrationTest extends BaseIntegrationTest {

  // TODO: As this class grows, consider if it's worth breaking down these workspace tests into
  //  different class files based on the the type of workspace action (Create, Get, Delete, etc).

  @Autowired private WorkspaceManagerTestClient workspaceManagerTestClient;
  @Autowired private TestUtils testUtils;
  @Autowired private IntegrationTestConfiguration testConfig;
  @Autowired private AuthService authService;
  private static final Logger logger = LoggerFactory.getLogger(WorkspaceIntegrationTest.class);
  private final ConcurrentHashMap<String, List<UUID>> testToWorkspaceIdsMap =
      new ConcurrentHashMap<>();

  // TODO: Create custom annotations for this tag, and also for the "integration" and "unit" tags in
  //  this test and elsewhere. AS-431.
  private final String TAG_NEEDS_CLEANUP = "needs-cleanup";

  /*
   * TODO: Cloud Resource Library (CRL) is planning to broadly address resource cleanup for integration tests in MC Terra
   *  applications.
   *  The doc below proposes a "Resource Tracking" approach for cleaning up. We should follow the developments in CRL for
   *  potential utilization here in Workspace Manager.
   *  Doc: https://docs.google.com/document/d/13mYVJML_fOLsX1gxQxRJgECUNT27dAMKzEJxUEvtQqM/edit#heading=h.x06ofvfgp7wt
   */
  @AfterEach
  void tearDown(TestInfo testInfo) throws Exception {
    Set<String> tags = testInfo.getTags();
    if (tags != null && tags.contains(TAG_NEEDS_CLEANUP)) {
      List<UUID> uuidList = testToWorkspaceIdsMap.get(testInfo.getDisplayName());
      if (uuidList != null) {
        cleanUpWorkspaces(uuidList);
      }
    }
  }

  @Test
  @Tag(TAG_NEEDS_CLEANUP)
  void createWorkspace(TestInfo testInfo) throws Exception {
    UUID workspaceId = UUID.randomUUID();
    testToWorkspaceIdsMap.put(testInfo.getDisplayName(), Collections.singletonList(workspaceId));

    WorkspaceResponse<CreatedWorkspace> workspaceResponse = createDefaultWorkspace(workspaceId);

    assertEquals(HttpStatus.OK, workspaceResponse.getStatusCode());
    assertTrue(workspaceResponse.isResponseObject());
    CreatedWorkspace createdWorkspace = workspaceResponse.getResponseObject();
    assertEquals(workspaceId, createdWorkspace.getId());
  }

  @Test
  @Tag(TAG_NEEDS_CLEANUP)
  void getWorkspace(TestInfo testInfo) throws Exception {
    UUID workspaceId = UUID.randomUUID();
    testToWorkspaceIdsMap.put(testInfo.getDisplayName(), Collections.singletonList(workspaceId));

    createDefaultWorkspace(workspaceId);

    String userEmail = testConfig.getUserEmail();
    String path = testConfig.getWsmWorkspacesBaseUrl() + "/" + workspaceId;

    WorkspaceResponse<WorkspaceDescription> getWorkspaceResponse =
        workspaceManagerTestClient.get(userEmail, path, WorkspaceDescription.class);

    assertEquals(HttpStatus.OK, getWorkspaceResponse.getStatusCode());
    assertTrue(getWorkspaceResponse.isResponseObject());
    WorkspaceDescription workspaceDescription = getWorkspaceResponse.getResponseObject();
    assertEquals(workspaceId, workspaceDescription.getId());
  }

  @Test
  @Tag(TAG_NEEDS_CLEANUP)
  void deleteWorkspace(TestInfo testInfo) throws Exception {
    UUID workspaceId = UUID.randomUUID();
    testToWorkspaceIdsMap.put(testInfo.getDisplayName(), Collections.singletonList(workspaceId));
    createDefaultWorkspace(workspaceId);

    String userEmail = testConfig.getUserEmail();
    authService.getAuthToken(userEmail);
    String path = testConfig.getWsmWorkspacesBaseUrl() + "/" + workspaceId;

    WorkspaceResponse<?> deleteWorkspaceResponse =
        workspaceManagerTestClient.delete(userEmail, path);

    assertEquals(HttpStatus.NO_CONTENT, deleteWorkspaceResponse.getStatusCode());

    /*
     Remove the workspace id from the map, so cleanup process won't try to delete an already-deleted workspace.
     The allows us to still clean up the workspace id in this test if the assertion above fails.

     Note: The cleanup does the same thing as the deleteWorkspace request in this test (i.e. they both call the same
     http endpoint with the same user, etc). So if the deleteWorkspace request in this test fails, there's a chance
     that the cleanup may also fail. This may not apply to non-persistent failures such as temporary unavailability,
     timeout, etc.
    */
    testToWorkspaceIdsMap.remove(testInfo.getDisplayName());
  }

  @Test
  @Tag(TAG_NEEDS_CLEANUP)
  void createDataReference(TestInfo testInfo) throws Exception {
    UUID workspaceId = UUID.randomUUID();
    testToWorkspaceIdsMap.put(testInfo.getDisplayName(), Collections.singletonList(workspaceId));

    createDefaultWorkspace(workspaceId);
    String dataReferenceName = "workspace_integration_test_snapshot";
    WorkspaceResponse<DataReferenceDescription> postResponse =
        createDefaultDataReference(workspaceId, dataReferenceName);

    assertEquals(HttpStatus.OK, postResponse.getStatusCode());
    assertTrue(postResponse.isResponseObject());
    DataReferenceDescription dataReferenceDescription = postResponse.getResponseObject();
    assertEquals(dataReferenceName, dataReferenceDescription.getName());
    assertEquals(workspaceId, dataReferenceDescription.getWorkspaceId());
    assertEquals(
        CloningInstructionsEnum.NOTHING, dataReferenceDescription.getCloningInstructions());
  }

  @Test
  @Tag(TAG_NEEDS_CLEANUP)
  void deleteDataReference(TestInfo testInfo) throws Exception {
    UUID workspaceId = UUID.randomUUID();
    testToWorkspaceIdsMap.put(testInfo.getDisplayName(), Collections.singletonList(workspaceId));

    createDefaultWorkspace(workspaceId);
    WorkspaceResponse<DataReferenceDescription> postResponse =
        createDefaultDataReference(workspaceId, "workspace_integration_test_snapshot");
    assertEquals(HttpStatus.OK, postResponse.getStatusCode());

    UUID referenceId = postResponse.getResponseObject().getReferenceId();
    String deletePath =
        testConfig.getWsmWorkspacesBaseUrl() + "/" + workspaceId + "/datareferences/" + referenceId;

    WorkspaceResponse<?> deleteResponse =
        workspaceManagerTestClient.delete(testConfig.getUserEmail(), deletePath);
    assertEquals(HttpStatus.NO_CONTENT, deleteResponse.getStatusCode());
  }

  @Test
  @Tag(TAG_NEEDS_CLEANUP)
  void listDataReference(TestInfo testInfo) throws Exception {
    UUID workspaceId = UUID.randomUUID();
    testToWorkspaceIdsMap.put(testInfo.getDisplayName(), Collections.singletonList(workspaceId));

    createDefaultWorkspace(workspaceId);
    // This creates two separate references to the same underlying snapshot, which is valid
    // in real workspaces.
    WorkspaceResponse<DataReferenceDescription> firstPostResponse =
        createDefaultDataReference(workspaceId, "workspace_integration_test_snapshot");
    WorkspaceResponse<DataReferenceDescription> secondPostResponse =
        createDefaultDataReference(workspaceId, "second_workspace_integration_test_snapshot");

    assertEquals(HttpStatus.OK, firstPostResponse.getStatusCode());
    assertEquals(HttpStatus.OK, secondPostResponse.getStatusCode());
    String path = testConfig.getWsmWorkspacesBaseUrl() + "/" + workspaceId + "/datareferences";
    WorkspaceResponse<DataReferenceList> listResponse =
        workspaceManagerTestClient.get(testConfig.getUserEmail(), path, DataReferenceList.class);
    assertEquals(HttpStatus.OK, listResponse.getStatusCode());
    assertTrue(listResponse.isResponseObject());
    DataReferenceList referenceList = listResponse.getResponseObject();
    assertEquals(referenceList.getResources().size(), 2);

    DataReferenceDescription[] expectedResults = {
      firstPostResponse.getResponseObject(), secondPostResponse.getResponseObject()
    };
    assertThat(referenceList.getResources(), containsInAnyOrder(expectedResults));
  }

  // TODO: limit and offset parameters to enumerateDataReference calls are validated in the
  // Controller, so they should be tested in integration tests.

  @Test
  @Tag(TAG_NEEDS_CLEANUP)
  void getDataReferenceById(TestInfo testInfo) throws Exception {
    UUID workspaceId = UUID.randomUUID();
    testToWorkspaceIdsMap.put(testInfo.getDisplayName(), Collections.singletonList(workspaceId));

    createDefaultWorkspace(workspaceId);
    String referenceName = "workspace_integration_test_snapshot";
    WorkspaceResponse<DataReferenceDescription> postResponse =
        createDefaultDataReference(workspaceId, referenceName);
    assertEquals(HttpStatus.OK, postResponse.getStatusCode());
    assertTrue(postResponse.isResponseObject());

    UUID referenceId = postResponse.getResponseObject().getReferenceId();
    String path =
        testConfig.getWsmWorkspacesBaseUrl() + "/" + workspaceId + "/datareferences/" + referenceId;

    WorkspaceResponse<DataReferenceDescription> getResponse =
        workspaceManagerTestClient.get(
            testConfig.getUserEmail(), path, DataReferenceDescription.class);

    assertEquals(HttpStatus.OK, getResponse.getStatusCode());
    assertTrue(getResponse.isResponseObject());
    DataReferenceDescription dataReferenceDescription = getResponse.getResponseObject();

    assertEquals(referenceName, dataReferenceDescription.getName());
    assertEquals(workspaceId, dataReferenceDescription.getWorkspaceId());
  }

  @Test
  @Tag(TAG_NEEDS_CLEANUP)
  void getDataReferenceByNameAndType(TestInfo testInfo) throws Exception {
    UUID workspaceId = UUID.randomUUID();
    testToWorkspaceIdsMap.put(testInfo.getDisplayName(), Collections.singletonList(workspaceId));

    createDefaultWorkspace(workspaceId);
    String referenceName = "workspace_integration_test_snapshot";
    String referenceType = ReferenceTypeEnum.DATA_REPO_SNAPSHOT.toString();
    WorkspaceResponse<DataReferenceDescription> postResponse =
        createDefaultDataReference(workspaceId, referenceName);
    assertEquals(HttpStatus.OK, postResponse.getStatusCode());
    assertTrue(postResponse.isResponseObject());

    UUID referenceId = postResponse.getResponseObject().getReferenceId();

    String path =
        String.format(
            "%s/%s/datareferences/%s/%s",
            testConfig.getWsmWorkspacesBaseUrl(), workspaceId, referenceType, referenceName);

    WorkspaceResponse<DataReferenceDescription> getResponse =
        workspaceManagerTestClient.get(
            testConfig.getUserEmail(), path, DataReferenceDescription.class);

    assertEquals(HttpStatus.OK, getResponse.getStatusCode());
    assertTrue(getResponse.isResponseObject());
    DataReferenceDescription dataReferenceDescription = getResponse.getResponseObject();

    assertEquals(referenceId, dataReferenceDescription.getReferenceId());
    assertEquals(workspaceId, dataReferenceDescription.getWorkspaceId());
  }

  private WorkspaceResponse<CreatedWorkspace> createDefaultWorkspace(UUID workspaceId)
      throws Exception {
    String path = testConfig.getWsmWorkspacesBaseUrl();
    String userEmail = testConfig.getUserEmail();
    CreateWorkspaceRequestBody body =
        new CreateWorkspaceRequestBody().id(workspaceId).stage(WorkspaceStageModel.MC_WORKSPACE);
    String jsonBody = testUtils.mapToJson(body);

    return workspaceManagerTestClient.post(userEmail, path, jsonBody, CreatedWorkspace.class);
  }

  private WorkspaceResponse<DataReferenceDescription> createDefaultDataReference(
      UUID workspaceId, String dataReferenceName) throws Exception {
    // This method relies on a persistent snapshot existing in Data Repo, currently in dev.
    // This snapshot was created using our dev service account, which is a steward in dev Data Repo.
    // First, I created a dataset "wm_integration_test_dataset" using TDR's
    // snapshot-test-dataset.json. Then, I created the snapshot
    // "workspace_integration_test_snapshot" using the "byFullView" mode. Finally, I added the
    // integration test user as a reader of this snapshot.
    // These steps should only need to be repeated if the dev DataRepo data is deleted, or to
    // support this test in other DataRepo environments.
    // Data Repo makes a reasonable effort to maintain their dev environment, so this should be a
    // very rare occurrence.
    String userEmail = testConfig.getUserEmail();
    String path = testConfig.getWsmWorkspacesBaseUrl() + "/" + workspaceId + "/datareferences";

    DataRepoSnapshot snapshotReference =
        new DataRepoSnapshot()
            .snapshot(testConfig.getDataRepoSnapshotIdFromEnv())
            .instanceName(testConfig.getDataRepoInstanceNameFromEnv());
    CreateDataReferenceRequestBody request =
        new CreateDataReferenceRequestBody()
            .name(dataReferenceName)
            .referenceType(ReferenceTypeEnum.DATA_REPO_SNAPSHOT)
            .reference(snapshotReference)
            .cloningInstructions(CloningInstructionsEnum.NOTHING);

    return workspaceManagerTestClient.post(
        userEmail, path, testUtils.mapToJson(request), DataReferenceDescription.class);
  }

  private void cleanUpWorkspaces(List<UUID> workspaceIds) throws Exception {
    /* TODO: Currently fetching token once here before cleaning up (potentially multiple workspaceIds) for each test
        method. Is it likely that a token will expire mid-cleanup (i.e. works for one delete, but expires before
        the next one)? In any case, caching the auth token will enable us to efficiently auth before EACH delete request.
        This ticket will implement caching for auth token using Caffeine AS-428
    */
    String userEmail = testConfig.getUserEmail();
    authService.getAuthToken(userEmail);
    String workspaceBaseUrl = testConfig.getWsmWorkspacesBaseUrl();

    for (UUID uuid : workspaceIds) {
      String path = workspaceBaseUrl + "/" + uuid;

      WorkspaceResponse<?> deleteWorkspaceResponse =
          workspaceManagerTestClient.delete(userEmail, path);

      /*
        TODO: If the delete call fails for some reason, we won't 'assert' as this is not a test. We
         log a warning (can log an error instead) to indicate the cleanup step failure. Can we somehow indicate this
         failure within GitHub workflow, BUT not actually fail the build? That way, we can easily see what builds (if any)
         have failed cleanup steps, and potentially take manual action. Note that this all may not be needed if/when
         we use Janitor Service or something similar that's proposed for MC Terra applications.
      */
      if (deleteWorkspaceResponse.getStatusCode() != HttpStatus.NO_CONTENT) {
        logger.warn("Clean up failed for workspace={} path={}", uuid.toString(), path);
      }
    }
  }
}
