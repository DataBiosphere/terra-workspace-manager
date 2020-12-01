package bio.terra.workspace.service.iam;

import bio.terra.workspace.common.BaseConnectedTest;
import bio.terra.workspace.service.workspace.WorkspaceService;
import bio.terra.workspace.service.workspace.model.Workspace;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

// TODO: these should be integration tests, since they're testing interactions between WM and Sam and should use valid credentials.
public class SamServiceTest extends BaseConnectedTest {

  @Autowired SamService samService;
  @Autowired WorkspaceService workspaceService;

  private AuthenticatedUserRequest testUser =
      new AuthenticatedUserRequest()
          .subjectId("SamUnitTest")
          .email("stairway@unit.com")
          .token(Optional.of("not-a-real-token"));

  @Test
  public void AddedReaderCanRead() throws Exception {
    Workspace workspace = workspaceService.createWorkspace()

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
}
