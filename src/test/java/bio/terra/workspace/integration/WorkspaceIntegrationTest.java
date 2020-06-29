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
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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

  @BeforeEach
  public void setup() {}

  @Test
  public void createWorkspace() throws Exception {
    // todo: Seeing that we have https://broadworkbench.atlassian.net/browse/AS-315 in the upcoming,
    //  Let's reuse the code to-be-written there to clean up workspaces after creating them
    //  Clean up manually for now
    UUID workspaceId = UUID.randomUUID();
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

    WorkspaceResponse<CreatedWorkspace> workspaceResponse =
        workspaceManagerTestClient.post(userEmail, path, json, CreatedWorkspace.class);

    Assertions.assertEquals(workspaceResponse.getStatusCode(), HttpStatus.OK);
    Assertions.assertTrue(workspaceResponse.isResponseObject());
    CreatedWorkspace createdWorkspace = workspaceResponse.getResponseObject();
    Assertions.assertEquals(workspaceId.toString(), createdWorkspace.getId());
  }

  @Test
  public void deleteWorkspace() throws Exception {
    String userEmail = testConfig.getServiceAccountEmail();
    String token = authService.getAuthToken(userEmail);
    String path = testConfig.getWsmWorkspaceBaseUrl();
    UUID workspaceId = UUID.randomUUID();

    CreateWorkspaceRequestBody createWorkspaceRequestBody =
        new CreateWorkspaceRequestBody()
            .id(workspaceId)
            .authToken(token)
            .spendProfile(null)
            .policies(null);
    String createWorkspaceRequestJson = testUtils.mapToJson(createWorkspaceRequestBody);

    WorkspaceResponse<CreatedWorkspace> createWorkspaceResponse =
        workspaceManagerTestClient.post(
            userEmail, path, createWorkspaceRequestJson, CreatedWorkspace.class);

    // todo: create ticket to implement caching for token
    path = testConfig.getWsmWorkspaceBaseUrl() + "/" + workspaceId;
    DeleteWorkspaceRequestBody body = new DeleteWorkspaceRequestBody().authToken(token);
    String deleteWorkspaceRequestJson = testUtils.mapToJson(body);

    WorkspaceResponse<?> deleteWorkspaceResponse =
        workspaceManagerTestClient.delete(userEmail, path, deleteWorkspaceRequestJson);

    Assertions.assertEquals(deleteWorkspaceResponse.getStatusCode(), HttpStatus.NO_CONTENT);
  }

}
