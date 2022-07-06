package bio.terra.workspace.common.utils;

import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * A collection of utilities and constants useful for MockMVC-based tests. This style of tests lets
 * us test controller-layer code (request/response parsing, authz, and validation) without actually
 * spinning up a local server.
 *
 * <p>TODO: it's probably worth looking into whether we can automatically pull routes from the
 * generated swagger, instead of manually wrapping them here.
 */
@Component
public class MockMvcUtils {

  public static final String AUTH_HEADER = "Authorization";

  public static final String CREATE_WORKSPACE_PATH_FORMAT = "/api/workspaces/v1";
  public static final String GET_WORKSPACE_PATH_FORMAT = "/api/workspaces/v1/%s";
  public static final String GET_WORKSPACE_BY_UFID_PATH_FORMAT =
      "/api/workspaces/v1/workspaceByUserFacingId/%s";
  public static final String DELETE_WORKSPACE_PATH_FORMAT = "/api/workspaces/v1/%s";
  public static final String CREATE_SNAPSHOT_PATH_FORMAT =
      "/api/workspaces/v1/%s/resources/referenced/datarepo/snapshots";
  public static final String CREATE_CLOUD_CONTEXT_PATH_FORMAT =
      "/api/workspaces/v1/%s/cloudcontexts";
  public static final String GET_CLOUD_CONTEXT_PATH_FORMAT =
      "/api/workspaces/v1/%s/cloudcontexts/result/%s";

  public static final String CREATE_AZURE_IP_PATH_FORMAT =
      "/api/workspaces/v1/%s/resources/controlled/azure/ip";
  public static final String CREATE_AZURE_DISK_PATH_FORMAT =
      "/api/workspaces/v1/%s/resources/controlled/azure/disks";
  public static final String CREATE_AZURE_NETWORK_PATH_FORMAT =
      "/api/workspaces/v1/%s/resources/controlled/azure/network";
  public static final String CREATE_AZURE_VM_PATH_FORMAT =
      "/api/workspaces/v1/%s/resources/controlled/azure/vm";

  @Autowired private ObjectMapper objectMapper;

  public static String authHeaderFromToken(AuthenticatedUserRequest userRequest) {
    return "Bearer " + userRequest.getRequiredToken();
  }

  public static MockHttpServletRequestBuilder addAuth(
      MockHttpServletRequestBuilder request, AuthenticatedUserRequest userRequest) {
    return request.header(AUTH_HEADER, "Bearer " + userRequest.getRequiredToken());
  }

  public static MockHttpServletRequestBuilder addJsonContentType(
      MockHttpServletRequestBuilder request) {
    return request.contentType("application/json");
  }

  // public ApiWorkspaceDescription mvcGetWorkspace(MockMvc mockMvc, UUID workspaceId,
  // AuthenticatedUserRequest userRequest, @Nullable
  //     HttpStatus expectedStatusCode) throws Exception {
  //   MvcResult result = mockMvc.perform(get(String.format(GET_WORKSPACE_PATH_FORMAT,
  // workspaceId)).header(MockMvcUtils.AUTH_HEADER,
  // MockMvcUtils.authHeaderFromToken(userRequest))).andExpect(status().is(
  //       expectedStatusCode == null ? HttpStatus.OK.value() :
  // expectedStatusCode.value())).andReturn();
  //   return objectMapper.readValue(result.getResponse().getContentAsString(),
  // ApiWorkspaceDescription.class);
  // }
  //
  // public ApiWorkspaceDescription mvcGetWorkspaceByUfid(MockMvc mockMvc, String ufid,
  // AuthenticatedUserRequest userRequest, @Nullable
  //     HttpStatus expectedStatusCode) throws Exception {
  //   MvcResult result = mockMvc.perform(get(String.format(GET_WORKSPACE_BY_UFID_PATH_FORMAT,
  // ufid)).header(MockMvcUtils.AUTH_HEADER,
  // MockMvcUtils.authHeaderFromToken(userRequest))).andExpect(status().is(
  //       expectedStatusCode == null ? HttpStatus.OK.value() :
  // expectedStatusCode.value())).andReturn();
  //   return objectMapper.readValue(result.getResponse().getContentAsString(),
  // ApiWorkspaceDescription.class);
  // }
  //
  // public ApiDataRepoSnapshotResource mvcCreateSnapshotReference(MockMvc mockMvc, UUID
  // workspaceId, AuthenticatedUserRequest userRequest, @Nullable HttpStatus expectedStatusCode)

}
