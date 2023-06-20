package bio.terra.workspace.service.workspace;

import static org.junit.jupiter.api.Assertions.assertThrows;

import bio.terra.workspace.common.BaseUnitTest;
import bio.terra.workspace.common.fixtures.WorkspaceFixtures;
import bio.terra.workspace.db.WorkspaceDao;
import bio.terra.workspace.db.exception.ResourceStateConflictException;
import bio.terra.workspace.service.logging.WorkspaceActivityLogService;
import bio.terra.workspace.service.policy.PolicyValidator;
import bio.terra.workspace.service.workspace.model.Workspace;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

public class WorkspaceStateUnitTest extends BaseUnitTest {
  @MockBean private PolicyValidator mockPolicyValidator;
  @MockBean private WorkspaceActivityLogService mockWorkspaceActivityLogService;

  @Autowired private WorkspaceService workspaceService;
  @Autowired private WorkspaceDao workspaceDao;

  @Test
  void createDeleteStateTest() throws Exception {
    Workspace workspace1 = WorkspaceFixtures.buildMcWorkspace();
    var flightId1 = UUID.randomUUID().toString();
    final var flightId2 = UUID.randomUUID().toString();
    workspaceDao.createWorkspaceStart(workspace1, /* applicationIds */ null, flightId1);
    assertThrows(
        ResourceStateConflictException.class,
        () -> workspaceDao.createWorkspaceSuccess(workspace1.workspaceId(), flightId2));

    workspaceDao.createWorkspaceSuccess(workspace1.workspaceId(), flightId1);

    var deleteFlightId1 = UUID.randomUUID().toString();
    final var deleteFlightId2 = UUID.randomUUID().toString();

    workspaceDao.deleteWorkspaceStart(workspace1.workspaceId(), deleteFlightId1);
    assertThrows(
        ResourceStateConflictException.class,
        () -> workspaceDao.deleteWorkspaceStart(workspace1.workspaceId(), deleteFlightId2));

    workspaceDao.deleteWorkspaceSuccess(workspace1.workspaceId(), deleteFlightId1);
  }
}
