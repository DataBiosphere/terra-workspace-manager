package bio.terra.workspace.service.iam;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import bio.terra.workspace.common.BaseConnectedTest;
import bio.terra.workspace.common.exception.SamUnauthorizedException;
import bio.terra.workspace.connected.UserAccessUtils;
import bio.terra.workspace.generated.model.CreateDataReferenceRequestBody;
import bio.terra.workspace.service.workspace.WorkspaceService;
import bio.terra.workspace.service.workspace.model.Workspace;
import bio.terra.workspace.service.workspace.model.WorkspaceRequest;
import bio.terra.workspace.service.workspace.model.WorkspaceStage;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class SamServiceTest extends BaseConnectedTest {

  @Autowired SamService samService;
  @Autowired WorkspaceService workspaceService;
  @Autowired UserAccessUtils userAccessUtils;

  @Test
  public void AddedReaderCanRead() {
    UUID workspaceId = createWorkspaceDefaultUser();
    // Before being granted permission, secondary user should be rejected.
    assertThrows(SamUnauthorizedException.class, () -> workspaceService.getWorkspace(workspaceId, secondaryUserRequest()));
    samService.addWorkspaceRole(secondaryUserRequest(), workspaceId, IamRole.READER);
    // After being granted permission, secondary user can read the workspace.
    Workspace readWorkspace = workspaceService.getWorkspace(workspaceId, secondaryUserRequest());
    assertEquals(readWorkspace.workspaceId(), workspaceId);
  }

  @Test
  public void AddedWriterCanWrite() throws Exception {

  }

  @Test
  public void RemovedReaderCannotRead() throws Exception {

  }

  @Test
  public void NonOwnerCannotAddReader() throws Exception {

  }

  @Test
  public void PermissionsApiFailsInRawlsWorkspace() throws Exception {

  }

  @Test
  public void InvalidUserEmailRejected() throws Exception {

  }

  /** Convenience wrapper to build an AuthenticatedUserRequest from utils' default user.
   *
   * This only fills in access token, not email or subjectId..
   * */
  private AuthenticatedUserRequest defaultUserRequest() {
    return new AuthenticatedUserRequest(null, null, Optional.of(userAccessUtils.defaultUserAccessToken().getTokenValue()));
  }

  /** Convenience wrapper to build an AuthenticatedUserRequest from utils' secondary default user.
   *
   * This only fills in access token, not email or subjectId.
   * */
  private AuthenticatedUserRequest secondaryUserRequest() {
    return new AuthenticatedUserRequest(null, null, Optional.of(userAccessUtils.secondUserAccessToken().getTokenValue()));
  }

  /** Create a workspace using the default test user for connected tests, return its ID.. */
  private UUID createWorkspaceDefaultUser() {
    WorkspaceRequest request = WorkspaceRequest.builder()
        .workspaceId(UUID.randomUUID())
        .workspaceStage(WorkspaceStage.MC_WORKSPACE)
        .build();
    return workspaceService.createWorkspace(request, defaultUserRequest());
  }
}
