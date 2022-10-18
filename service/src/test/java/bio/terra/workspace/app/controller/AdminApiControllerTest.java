package bio.terra.workspace.app.controller;

import static bio.terra.workspace.common.utils.MockMvcUtils.ADMIN_V1_CLOUD_CONTEXTS_SYNC_IAM_PATH;
import static bio.terra.workspace.common.utils.MockMvcUtils.USER_REQUEST;
import static bio.terra.workspace.common.utils.MockMvcUtils.addAuth;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import bio.terra.common.exception.ForbiddenException;
import bio.terra.workspace.common.BaseConnectedTest;
import bio.terra.workspace.connected.UserAccessUtils;
import bio.terra.workspace.connected.WorkspaceConnectedTestUtils;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.workspace.model.Workspace;
import org.apache.http.HttpStatus;
import org.broadinstitute.dsde.workbench.client.sam.model.UserStatusInfo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

public class AdminApiControllerTest extends BaseConnectedTest {

  // mock out samService because the test user doesn't have admin access.
  @MockBean SamService samService;

  @Autowired MockMvc mockMvc;
  @Autowired UserAccessUtils userAccessUtils;
  @Autowired WorkspaceConnectedTestUtils workspaceConnectedTestUtils;

  private Workspace workspace1;

  @BeforeEach
  void setUp() throws InterruptedException {
    workspace1 =
        workspaceConnectedTestUtils.createWorkspaceWithGcpContext(
            userAccessUtils.defaultUserAuthRequest());
    when(samService.getUserStatusInfo(any()))
        .thenReturn(
            new UserStatusInfo()
                .userEmail(USER_REQUEST.getEmail())
                .userSubjectId(USER_REQUEST.getSubjectId()));
  }

  @AfterEach
  void cleanUp() {
    workspaceConnectedTestUtils.deleteWorkspaceAndGcpContext(
        userAccessUtils.defaultUserAuthRequest(), workspace1.getWorkspaceId());
  }

  @Test
  void syncIamRole() throws Exception {
    doNothing().when(samService).checkAdminAuthz(any(AuthenticatedUserRequest.class));

    mockMvc
        .perform(
            addAuth(
                patch(ADMIN_V1_CLOUD_CONTEXTS_SYNC_IAM_PATH),
                userAccessUtils.defaultUserAuthRequest()))
        .andExpect(status().is(HttpStatus.SC_OK));
  }

  @Test
  void syncIamRole_notAdmin_throws403() throws Exception {
    doThrow(new ForbiddenException("no access"))
        .when(samService)
        .checkAdminAuthz(any(AuthenticatedUserRequest.class));

    mockMvc
        .perform(
            addAuth(
                patch(ADMIN_V1_CLOUD_CONTEXTS_SYNC_IAM_PATH),
                userAccessUtils.defaultUserAuthRequest()))
        .andExpect(status().is(HttpStatus.SC_FORBIDDEN));
  }
}
