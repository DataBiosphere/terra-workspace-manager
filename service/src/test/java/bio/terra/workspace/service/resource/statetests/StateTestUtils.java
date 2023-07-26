package bio.terra.workspace.service.resource.statetests;

import static bio.terra.workspace.common.utils.MockMvcUtils.USER_REQUEST;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import bio.terra.workspace.common.mocks.MockWorkspaceV1Api;
import bio.terra.workspace.common.utils.MockMvcUtils;
import java.util.UUID;
import org.apache.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;

public class StateTestUtils {
  private final MockMvc mockMvc;
  private final MockMvcUtils mockMvcUtils;
  private final MockWorkspaceV1Api mockWorkspaceV1Api;

  public StateTestUtils(
      MockMvc mockMvc, MockMvcUtils mockMvcUtils, MockWorkspaceV1Api mockWorkspaceV1Api) {
    this.mockMvc = mockMvc;
    this.mockMvcUtils = mockMvcUtils;
    this.mockWorkspaceV1Api = mockWorkspaceV1Api;
  }

  void patchResourceExpectConflict(
      UUID workspaceId, UUID resourceId, String pathFormat, String request) throws Exception {
    mockMvcUtils.patchExpect(
        USER_REQUEST,
        request,
        pathFormat.formatted(workspaceId, resourceId),
        HttpStatus.SC_CONFLICT);
  }

  void postResourceExpectConflict(
      UUID workspaceId, UUID resourceId, String pathFormat, String request) throws Exception {
    mockMvcUtils.postExpect(
        USER_REQUEST,
        request,
        pathFormat.formatted(workspaceId, resourceId),
        HttpStatus.SC_CONFLICT);
  }

  void deleteResourceExpectConflict(UUID workspaceId, UUID resourceId, String pathFormat)
      throws Exception {
    mockMvc
        .perform(
            MockMvcUtils.addAuth(
                delete(String.format(pathFormat, workspaceId, resourceId)), USER_REQUEST))
        .andExpect(status().is(HttpStatus.SC_CONFLICT));
  }

  <T> void updateControlledResource(
      Class<T> clazz, UUID workspaceUuid, UUID resourceId, String pathFormat, String body)
      throws Exception {
    mockWorkspaceV1Api.updateResourceAndExpect(
        clazz, pathFormat, workspaceUuid, resourceId, body, USER_REQUEST, HttpStatus.SC_CONFLICT);
  }
}
