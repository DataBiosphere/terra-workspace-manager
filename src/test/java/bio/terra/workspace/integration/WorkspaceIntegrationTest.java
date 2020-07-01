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
import java.util.UUID;
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

  @Autowired private WorkspaceManagerTestClient workspaceManagerTestClient;
  @Autowired private TestUtils testUtils;
  @Autowired private TestConfiguration testConfig;
  @Autowired private AuthService authService;
  private UUID workspaceIdToCleanup;
  private final String TAG_NEEDS_CLEANUP = "needs-cleanup";
  private static final Logger logger = LoggerFactory.getLogger(WorkspaceIntegrationTest.class);

  // todo: As this class grows, consider if it's worth breaking down workspace tests into different
  // classes based on the
  // todo: the type of action (Create, Get, Delete, etc).

  @BeforeEach
  public void setup() {}

  // todo: add negative tests
  // todo: visibility when cleanup fails. Maybe fail step but not build (if that's possible with
  // GitHub Actions)?
  // todo: merge with master before committing/pushing
  // todo: note Janitor service work that's coming up. The current implementation depends on delete
  // endpoint always working
  @AfterEach
  public void tearDown(TestInfo testInfo) throws Exception {
    if (testInfo.getTags().contains(TAG_NEEDS_CLEANUP)) {
      cleanUpWorkspace();
    }
  }

  @Test
  @Tag(TAG_NEEDS_CLEANUP)
  public void createWorkspace() throws Exception {
    // todo: Seeing that we have https://broadworkbench.atlassian.net/browse/AS-315 in the upcoming,
    //  Let's reuse the code to-be-written there to clean up workspaces after creating them
    //  Clean up manually for now
    UUID workspaceId = UUID.randomUUID();
    workspaceIdToCleanup = workspaceId;
    WorkspaceResponse<CreatedWorkspace> workspaceResponse = createDefaultWorkspace(workspaceId);

    Assertions.assertEquals(workspaceResponse.getStatusCode(), HttpStatus.OK);
    Assertions.assertTrue(workspaceResponse.isResponseObject());
    CreatedWorkspace createdWorkspace = workspaceResponse.getResponseObject();
    Assertions.assertEquals(workspaceId.toString(), createdWorkspace.getId());
  }

  @Test
  public void deleteWorkspace() throws Exception {
    String userEmail = testConfig.getServiceAccountEmail();
    String token = authService.getAuthToken(userEmail);
    UUID workspaceId = UUID.randomUUID();

    WorkspaceResponse<CreatedWorkspace> workspaceResponse = createDefaultWorkspace(workspaceId);

    // todo: create ticket to implement caching for token, there's a need as the number of
    // getAuthToken calls grows
    String path = testConfig.getWsmWorkspaceBaseUrl() + "/" + workspaceId;

    DeleteWorkspaceRequestBody body = new DeleteWorkspaceRequestBody().authToken(token);
    String deleteWorkspaceRequestJson = testUtils.mapToJson(body);
    WorkspaceResponse<?> deleteWorkspaceResponse =
        workspaceManagerTestClient.delete(userEmail, path, deleteWorkspaceRequestJson);

    Assertions.assertEquals(deleteWorkspaceResponse.getStatusCode(), HttpStatus.NO_CONTENT);
  }

  private WorkspaceResponse<CreatedWorkspace> createDefaultWorkspace(UUID workspaceId)
      throws Exception {
    String path = testConfig.getWsmWorkspaceBaseUrl();
    String userEmail = testConfig.getServiceAccountEmail();
    String token = authService.getAuthToken(userEmail);
    CreateWorkspaceRequestBody body =
        new CreateWorkspaceRequestBody()
            .id(workspaceId)
            .authToken(token)
            .spendProfile(null)
            .policies(null);
    String json = testUtils.mapToJson(body);

    return workspaceManagerTestClient.post(userEmail, path, json, CreatedWorkspace.class);
  }

  private void cleanUpWorkspace() throws Exception {
    // todo: create ticket to implement caching for token, there's a need as the number of
    // getAuthToken calls grows
    String userEmail = testConfig.getServiceAccountEmail();
    String token = authService.getAuthToken(userEmail);
    String path = testConfig.getWsmWorkspaceBaseUrl() + "/" + workspaceIdToCleanup;

    DeleteWorkspaceRequestBody body = new DeleteWorkspaceRequestBody().authToken(token);
    String deleteWorkspaceRequestJson = testUtils.mapToJson(body);

    WorkspaceResponse<?> deleteWorkspaceResponse =
        workspaceManagerTestClient.delete(userEmail, path, deleteWorkspaceRequestJson);

    /*
      If the delete call fails for some reason, we won't 'assert' as this is not a test. We
      print an error to indicate the cleanup step failure. Can we somehow indicate the failure with in GitHub
      workflow, BUT not actually fail a build. That way, we know what cleanup runs failed, and potentially
      take manual action.
    */
    if (deleteWorkspaceResponse.getStatusCode() != HttpStatus.NO_CONTENT) {
      logger.info(
          "Clean up failed for workspace={} path={}", workspaceIdToCleanup.toString(), path);
    }
  }
}
