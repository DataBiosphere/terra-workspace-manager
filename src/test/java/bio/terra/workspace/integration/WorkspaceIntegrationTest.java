package bio.terra.workspace.integration;

import bio.terra.workspace.app.Main;
import bio.terra.workspace.generated.model.CreateWorkspaceRequestBody;
import bio.terra.workspace.generated.model.CreatedWorkspace;
import bio.terra.workspace.integration.common.auth.AuthService;
import bio.terra.workspace.integration.common.configuration.TestConfiguration;
import bio.terra.workspace.integration.common.response.WorkspaceResponse;
import bio.terra.workspace.integration.common.utils.TestUtils;
import bio.terra.workspace.integration.common.utils.WorkspaceManagerTestClient;
import java.util.UUID;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@Tag("integration")
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = Main.class)
@SpringBootTest
@AutoConfigureMockMvc
public class WorkspaceIntegrationTest {

  @Autowired private WorkspaceManagerTestClient workspaceManagerTestClient;

  @Autowired private TestUtils testUtils;

  @Autowired private TestConfiguration testConfig;

  @Autowired private AuthService authService;

  @BeforeEach
  public void setup() {}

  @Test
  public void createWorkspace() throws Exception {
    UUID workspaceId = UUID.randomUUID();
    String path = testConfig.getCreateWorkspaceUrlDev();
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
    Assertions.assertNotNull(workspaceResponse.getResponseObject());
    CreatedWorkspace createdWorkspace = workspaceResponse.getResponseObject().get();
    Assertions.assertEquals(workspaceId.toString(), createdWorkspace.getId());
  }
}