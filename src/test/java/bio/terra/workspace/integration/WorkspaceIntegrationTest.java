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
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.*;
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
      String testName = testInfo.getDisplayName();
      cleanUpWorkspaces(testToWorkspaceIdsMap.get(testName));
    }
  }

  @Test
  @Tag(TAG_NEEDS_CLEANUP)
  public void createWorkspace(TestInfo testInfo) throws Exception {
    UUID workspaceId = UUID.randomUUID();
    testToWorkspaceIdsMap.put(testInfo.getDisplayName(), Collections.singletonList(workspaceId));

    WorkspaceResponse<CreatedWorkspace> workspaceResponse = createDefaultWorkspace(workspaceId);

    Assertions.assertEquals(workspaceResponse.getStatusCode(), HttpStatus.OK);
    Assertions.assertTrue(workspaceResponse.isResponseObject());
    CreatedWorkspace createdWorkspace = workspaceResponse.getResponseObject();
    Assertions.assertEquals(workspaceId.toString(), createdWorkspace.getId());
  }

  @Test
  public void deleteWorkspace() throws Exception {
    UUID workspaceId = UUID.randomUUID();
    WorkspaceResponse<CreatedWorkspace> workspaceResponse = createDefaultWorkspace(workspaceId);

    String userEmail = testConfig.getServiceAccountEmail();
    String token = authService.getAuthToken(userEmail);
    String path = testConfig.getWsmWorkspacesBaseUrl() + "/" + workspaceId;
    DeleteWorkspaceRequestBody body = new DeleteWorkspaceRequestBody().authToken(token);
    String jsonBody = testUtils.mapToJson(body);

    WorkspaceResponse<?> deleteWorkspaceResponse =
        workspaceManagerTestClient.delete(userEmail, path, jsonBody);

    Assertions.assertEquals(deleteWorkspaceResponse.getStatusCode(), HttpStatus.NO_CONTENT);
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

    Assertions.assertEquals(deleteWorkspaceResponse.getStatusCode(), HttpStatus.UNAUTHORIZED);
    Assertions.assertTrue(deleteWorkspaceResponse.isErrorObject());
  }

  private WorkspaceResponse<CreatedWorkspace> createDefaultWorkspace(UUID workspaceId)
      throws Exception {
    String path = testConfig.getWsmWorkspacesBaseUrl();
    String userEmail = testConfig.getServiceAccountEmail();
    String token = authService.getAuthToken(userEmail);
    CreateWorkspaceRequestBody body =
        new CreateWorkspaceRequestBody()
            .id(workspaceId)
            .authToken(token)
            .spendProfile(null)
            .policies(null);
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
