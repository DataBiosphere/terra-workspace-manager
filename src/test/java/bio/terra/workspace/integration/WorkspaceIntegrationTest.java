package bio.terra.workspace.integration;

import bio.terra.workspace.app.Main;
import bio.terra.workspace.integration.common.auth.AuthService;
import bio.terra.workspace.integration.common.configuration.TestConfiguration;
import bio.terra.workspace.integration.common.response.WorkspaceResponse;
import bio.terra.workspace.integration.common.utils.TestUtils;
import bio.terra.workspace.integration.common.utils.WorkspaceManagerTestClient;
import bio.terra.workspace.model.CreateWorkspaceRequestBody;
import bio.terra.workspace.model.CreatedWorkspace;
import bio.terra.workspace.model.DeleteWorkspaceRequestBody;
import bio.terra.workspace.model.WorkspaceDescription;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@Tag("integration")
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = Main.class)
@SpringBootTest
@TestPropertySource("classpath:application-integration-test.properties")
public class WorkspaceIntegrationTest {

  // TODO: As this class grows, consider if it's worth breaking down these workspace tests into
  //  different class files based on the the type of workspace action (Create, Get, Delete, etc).

  @Autowired private WorkspaceManagerTestClient workspaceManagerTestClient;
  @Autowired private TestUtils testUtils;
  @Autowired private TestConfiguration testConfig;
  @Autowired private AuthService authService;
  private static final Logger logger = LoggerFactory.getLogger(WorkspaceIntegrationTest.class);
  private final ConcurrentHashMap<String, List<UUID>> testToWorkspaceIdsMap =
      new ConcurrentHashMap<>();

  // TODO: Create custom annotations for this tag, and also for the "integration" and "unit" tags in
  //  this test and elsewhere. AS-431.
  private final String TAG_NEEDS_CLEANUP = "needs-cleanup";

  @BeforeEach
  public void setup() {}

  /*
   * TODO: Cloud Resource Library (CRL) is planning to broadly address resource cleanup for integration tests in MC Terra
   *  applications.
   *  The doc below proposes a "Resource Tracking" approach for cleaning up. We should follow the developments in CRL for
   *  potential utilization here in Workspace Manager.
   *  Doc: https://docs.google.com/document/d/13mYVJML_fOLsX1gxQxRJgECUNT27dAMKzEJxUEvtQqM/edit#heading=h.x06ofvfgp7wt
   */
  @AfterEach
  public void tearDown(TestInfo testInfo) throws Exception {
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
  public void createWorkspace(TestInfo testInfo) throws Exception {
    UUID workspaceId = UUID.randomUUID();
    testToWorkspaceIdsMap.put(testInfo.getDisplayName(), Collections.singletonList(workspaceId));

    WorkspaceResponse<CreatedWorkspace> workspaceResponse = createDefaultWorkspace(workspaceId);

    Assertions.assertEquals(HttpStatus.OK, workspaceResponse.getStatusCode());
    Assertions.assertTrue(workspaceResponse.isResponseObject());
    CreatedWorkspace createdWorkspace = workspaceResponse.getResponseObject();
    Assertions.assertEquals(workspaceId, createdWorkspace.getId());
  }

  @Test
  @Tag(TAG_NEEDS_CLEANUP)
  public void getWorkspace(TestInfo testInfo) throws Exception {
    UUID workspaceId = UUID.randomUUID();
    testToWorkspaceIdsMap.put(testInfo.getDisplayName(), Collections.singletonList(workspaceId));

    createDefaultWorkspace(workspaceId);

    String userEmail = testConfig.getServiceAccountEmail();
    String path = testConfig.getWsmWorkspacesBaseUrl() + "/" + workspaceId;

    WorkspaceResponse<WorkspaceDescription> getWorkspaceResponse =
        workspaceManagerTestClient.get(userEmail, path, WorkspaceDescription.class);

    Assertions.assertEquals(HttpStatus.OK, getWorkspaceResponse.getStatusCode());
    Assertions.assertTrue(getWorkspaceResponse.isResponseObject());
    WorkspaceDescription workspaceDescription = getWorkspaceResponse.getResponseObject();
    Assertions.assertEquals(workspaceId, workspaceDescription.getId());
  }

  @Test
  @Tag(TAG_NEEDS_CLEANUP)
  public void deleteWorkspace(TestInfo testInfo) throws Exception {
    UUID workspaceId = UUID.randomUUID();
    testToWorkspaceIdsMap.put(testInfo.getDisplayName(), Collections.singletonList(workspaceId));
    WorkspaceResponse<CreatedWorkspace> workspaceResponse = createDefaultWorkspace(workspaceId);

    String userEmail = testConfig.getServiceAccountEmail();
    String token = authService.getAuthToken(userEmail);
    String path = testConfig.getWsmWorkspacesBaseUrl() + "/" + workspaceId;
    DeleteWorkspaceRequestBody body = new DeleteWorkspaceRequestBody().authToken(token);
    String jsonBody = testUtils.mapToJson(body);

    WorkspaceResponse<?> deleteWorkspaceResponse =
        workspaceManagerTestClient.delete(userEmail, path, jsonBody);

    Assertions.assertEquals(HttpStatus.NO_CONTENT, deleteWorkspaceResponse.getStatusCode());

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
  public void deleteWorkspaceWithInvalidToken(TestInfo testInfo) throws Exception {
    UUID workspaceId = UUID.randomUUID();
    testToWorkspaceIdsMap.put(testInfo.getDisplayName(), Collections.singletonList(workspaceId));
    WorkspaceResponse<CreatedWorkspace> workspaceResponse = createDefaultWorkspace(workspaceId);

    String userEmail = testConfig.getServiceAccountEmail();
    String token = "invalidToken";
    String path = testConfig.getWsmWorkspacesBaseUrl() + "/" + workspaceId;
    DeleteWorkspaceRequestBody body = new DeleteWorkspaceRequestBody().authToken(token);
    String jsonBody = testUtils.mapToJson(body);

    WorkspaceResponse<?> deleteWorkspaceResponse =
        workspaceManagerTestClient.delete(userEmail, path, jsonBody);

    Assertions.assertEquals(HttpStatus.UNAUTHORIZED, deleteWorkspaceResponse.getStatusCode());
    Assertions.assertTrue(deleteWorkspaceResponse.isErrorObject());
  }

  private WorkspaceResponse<CreatedWorkspace> createDefaultWorkspace(UUID workspaceId)
      throws Exception {
    String path = testConfig.getWsmWorkspacesBaseUrl();
    String userEmail = testConfig.getServiceAccountEmail();
    String token = authService.getAuthToken(userEmail);
    CreateWorkspaceRequestBody body =
        new CreateWorkspaceRequestBody().id(workspaceId).authToken(token);
    String jsonBody = testUtils.mapToJson(body);

    return workspaceManagerTestClient.post(userEmail, path, jsonBody, CreatedWorkspace.class);
  }

  private void cleanUpWorkspaces(List<UUID> workspaceIds) throws Exception {
    /* TODO: Currently fetching token once here before cleaning up (potentially multiple workspaceIds) for each test
        method. Is it likely that a token will expire mid-cleanup (i.e. works for one delete, but expires before
        the next one)? In any case, caching the auth token will enable us to efficiently auth before EACH delete request.
        This ticket will implement caching for auth token using Caffeine AS-428
    */
    String userEmail = testConfig.getServiceAccountEmail();
    String token = authService.getAuthToken(userEmail);
    String workspaceBaseUrl = testConfig.getWsmWorkspacesBaseUrl();
    DeleteWorkspaceRequestBody body = new DeleteWorkspaceRequestBody().authToken(token);
    String jsonBody = testUtils.mapToJson(body);

    for (UUID uuid : workspaceIds) {
      String path = workspaceBaseUrl + "/" + uuid;

      WorkspaceResponse<?> deleteWorkspaceResponse =
          workspaceManagerTestClient.delete(userEmail, path, jsonBody);

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
