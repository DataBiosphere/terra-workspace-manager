package bio.terra.workspace.app.configuration.external.controller;

import static bio.terra.workspace.common.utils.MockMvcUtils.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import bio.terra.workspace.common.BaseConnectedTest;
import bio.terra.workspace.common.fixtures.WorkspaceFixtures;
import bio.terra.workspace.generated.model.ApiCreatedWorkspace;
import bio.terra.workspace.generated.model.ApiWorkspaceDescription;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.iam.model.SamConstants.SamResource;
import bio.terra.workspace.service.iam.model.SamConstants.SamSpendProfileAction;
import bio.terra.workspace.service.iam.model.WsmIamRole;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Optional;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * An example of a mockMvc-based unit test for a controller.
 *
 * <p>In general, we would like to move towards testing new endpoints via controller instead of
 * calling services directly like we have in the past. Although this test duplicates coverage
 * currently in WorkspaceServiceTest, it's intended as a proof-of-concept for future mockMvc-based
 * tests.
 */
@AutoConfigureMockMvc
public class WorkspaceApiControllerTest extends BaseConnectedTest {

  AuthenticatedUserRequest USER_REQUEST =
      new AuthenticatedUserRequest(
          "fake@email.com", "subjectId123456", Optional.of("ThisIsNotARealBearerToken"));

  @Autowired MockMvc mockMvc;
  @Autowired ObjectMapper objectMapper;
  @MockBean SamService mockSamService;

  @BeforeEach
  public void setup() throws InterruptedException {
    Mockito.when(
            mockSamService.isAuthorized(
                Mockito.any(),
                eq(SamResource.SPEND_PROFILE),
                Mockito.any(),
                eq(SamSpendProfileAction.LINK)))
        .thenReturn(true);
    Mockito.when(mockSamService.listRequesterRoles(Mockito.any(), Mockito.any(), Mockito.any()))
        .thenReturn(List.of(WsmIamRole.OWNER));
  }

  @Test
  public void getWorkspace() throws Exception {
    ApiCreatedWorkspace workspace = createDefaultWorkspace();
    String serializedGetResponse =
        mockMvc
            .perform(
                addAuth(
                    get(String.format(GET_WORKSPACE_PATH_FORMAT, workspace.getId())), USER_REQUEST))
            .andExpect(status().is(HttpStatus.SC_OK))
            .andReturn()
            .getResponse()
            .getContentAsString();
    ApiWorkspaceDescription fetchedWorkspace =
        objectMapper.readValue(serializedGetResponse, ApiWorkspaceDescription.class);
    assertEquals(workspace.getId(), fetchedWorkspace.getId());
  }

  private ApiCreatedWorkspace createDefaultWorkspace() throws Exception {
    var createRequest = WorkspaceFixtures.createWorkspaceRequestBody();
    String serializedResponse =
        mockMvc
            .perform(
                addJsonContentType(
                    addAuth(
                        post(CREATE_WORKSPACE_PATH_FORMAT)
                            .content(objectMapper.writeValueAsString(createRequest)),
                        USER_REQUEST)))
            .andExpect(status().is(HttpStatus.SC_OK))
            .andReturn()
            .getResponse()
            .getContentAsString();
    return objectMapper.readValue(serializedResponse, ApiCreatedWorkspace.class);
  }
}
