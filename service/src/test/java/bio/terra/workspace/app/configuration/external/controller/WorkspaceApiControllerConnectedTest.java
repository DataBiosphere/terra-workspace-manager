package bio.terra.workspace.app.configuration.external.controller;

import static bio.terra.workspace.common.utils.MockMvcUtils.GRANT_ROLE_PATH_FORMAT;
import static bio.terra.workspace.common.utils.MockMvcUtils.WORKSPACES_V1_BY_UUID_PATH_FORMAT;
import static bio.terra.workspace.common.utils.MockMvcUtils.WORKSPACES_V1_PATH;
import static bio.terra.workspace.common.utils.MockMvcUtils.addAuth;
import static bio.terra.workspace.common.utils.MockMvcUtils.addJsonContentType;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import bio.terra.workspace.common.BaseConnectedTest;
import bio.terra.workspace.common.fixtures.WorkspaceFixtures;
import bio.terra.workspace.connected.UserAccessUtils;
import bio.terra.workspace.generated.model.ApiCreatedWorkspace;
import bio.terra.workspace.generated.model.ApiGrantRoleRequestBody;
import bio.terra.workspace.generated.model.ApiWorkspaceDescription;
import bio.terra.workspace.generated.model.ApiWorkspaceDescriptionList;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.model.WsmIamRole;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.UUID;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Connected tests for WorkspaceApiController.
 *
 * <p>In general, we would like to move towards testing new endpoints via controller instead of
 * calling services directly like we have in the past. Although this test duplicates coverage
 * currently in WorkspaceServiceTest, it's intended as a proof-of-concept for future mockMvc-based
 * tests.
 *
 * <p>Use this instead of WorkspaceApiControllerTest, if you want to use real
 * bio.terra.workspace.service.iam.SamService.
 */
@AutoConfigureMockMvc
public class WorkspaceApiControllerConnectedTest extends BaseConnectedTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  // @Autowired private SamService samService;
  @Autowired private UserAccessUtils userAccessUtils;

  @Test
  public void getWorkspace_doesNotReturnWorkspaceWithOnlyDiscovererRole() throws Exception {
    ApiCreatedWorkspace workspace = createWorkspace();

    grantRole(workspace.getId(), WsmIamRole.DISCOVERER, userAccessUtils.getSecondUserEmail());

    getWorkspaceDescriptionExpectingError(
        userAccessUtils.secondUserAuthRequest(), workspace.getId(), HttpStatus.SC_FORBIDDEN);
  }

  @Test
  public void listWorkspaces_doesNotReturnWorkspaceWithOnlyDiscovererRole() throws Exception {
    ApiCreatedWorkspace workspace = createWorkspace();

    grantRole(workspace.getId(), WsmIamRole.DISCOVERER, userAccessUtils.getSecondUserEmail());

    List<ApiWorkspaceDescription> listedWorkspaces =
        listWorkspaces(userAccessUtils.secondUserAuthRequest());
    assertTrue(listedWorkspaces.isEmpty());
  }

  private ApiCreatedWorkspace createWorkspace() throws Exception {
    var createRequest = WorkspaceFixtures.createWorkspaceRequestBody();
    String serializedResponse =
        mockMvc
            .perform(
                addJsonContentType(
                    addAuth(
                        post(WORKSPACES_V1_PATH)
                            .content(objectMapper.writeValueAsString(createRequest)),
                        userAccessUtils.defaultUserAuthRequest())))
            .andExpect(status().is(HttpStatus.SC_OK))
            .andReturn()
            .getResponse()
            .getContentAsString();
    return objectMapper.readValue(serializedResponse, ApiCreatedWorkspace.class);
  }

  private void getWorkspaceDescriptionExpectingError(
      AuthenticatedUserRequest userRequest, UUID id, int statusCode) throws Exception {
    mockMvc
        .perform(addAuth(get(String.format(WORKSPACES_V1_BY_UUID_PATH_FORMAT, id)), userRequest))
        .andExpect(status().is(statusCode));
  }

  private List<ApiWorkspaceDescription> listWorkspaces(AuthenticatedUserRequest request)
      throws Exception {
    String serializedResponse =
        mockMvc
            .perform(addAuth(get(WORKSPACES_V1_PATH), request))
            .andExpect(status().is(HttpStatus.SC_OK))
            .andReturn()
            .getResponse()
            .getContentAsString();
    return objectMapper
        .readValue(serializedResponse, ApiWorkspaceDescriptionList.class)
        .getWorkspaces();
  }

  private void grantRole(UUID workspaceId, WsmIamRole role, String memberEmail) throws Exception {
    var requestBody = new ApiGrantRoleRequestBody().memberEmail(memberEmail);
    mockMvc
        .perform(
            addJsonContentType(
                addAuth(
                    post(String.format(GRANT_ROLE_PATH_FORMAT, workspaceId, role.name()))
                        .content(objectMapper.writeValueAsString(requestBody)),
                    userAccessUtils.defaultUserAuthRequest())))
        .andExpect(status().is(HttpStatus.SC_NO_CONTENT))
        .andReturn()
        .getResponse()
        .getContentAsString();
  }
}
