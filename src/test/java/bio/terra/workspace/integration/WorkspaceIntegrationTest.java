package bio.terra.workspace.integration;

import bio.terra.workspace.app.Main;
import bio.terra.workspace.integration.common.auth.AuthService;
import bio.terra.workspace.integration.common.configuration.TestConfiguration;
import bio.terra.workspace.integration.common.response.WorkspaceResponse;
import bio.terra.workspace.integration.common.utils.TestUtils;
import bio.terra.workspace.integration.common.utils.WorkspaceManagerTestClient;
import java.util.UUID;
import bio.terra.workspace.model.CreateWorkspaceRequestBody;
import bio.terra.workspace.model.CreatedWorkspace;
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
    String path = testConfig.getWsmCreateWorkspaceUrl();
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
    CreatedWorkspace createdWorkspace = workspaceResponse.getData().getOrNull();
    Assertions.assertNotNull(createdWorkspace);
    Assertions.assertEquals(workspaceId.toString(), createdWorkspace.getId());
  }

}
