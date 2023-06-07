package bio.terra.workspace.service.resource.statetests;

import static bio.terra.workspace.common.utils.MockMvcUtils.USER_REQUEST;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import bio.terra.workspace.common.utils.MockMvcUtils;
import bio.terra.workspace.db.ResourceDao;
import bio.terra.workspace.service.resource.model.WsmResource;
import java.util.UUID;
import org.apache.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;

public class StateTestUtils {
  private final MockMvc mockMvc;
  private final MockMvcUtils mockMvcUtils;

  public StateTestUtils(MockMvc mockMvc, MockMvcUtils mockMvcUtils) {
    this.mockMvc = mockMvc;
    this.mockMvcUtils = mockMvcUtils;
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

  void createControlledResourceInDb(WsmResource resource, ResourceDao resourceDao) {
    // insertControlledResourceRow
    String flightId = UUID.randomUUID().toString();
    resourceDao.createResourceStart(resource, flightId);
    resourceDao.createResourceSuccess(resource, flightId);
  }

  <T> void updateControlledResource(
      Class<T> clazz, UUID workspaceUuid, UUID resourceId, String pathFormat, String body)
      throws Exception {
    mockMvcUtils.updateResource(
        clazz, pathFormat, workspaceUuid, resourceId, body, USER_REQUEST, HttpStatus.SC_CONFLICT);
  }
}
