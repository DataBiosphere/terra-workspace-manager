package bio.terra.workspace.service.workspace;

import static org.junit.jupiter.api.Assertions.assertThrows;

import bio.terra.workspace.common.BaseUnitTest;
import bio.terra.workspace.common.fixtures.WorkspaceFixtures;
import bio.terra.workspace.db.WorkspaceDao;
import bio.terra.workspace.db.exception.ResourceStateConflictException;
import bio.terra.workspace.service.workspace.model.Workspace;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class WorkspaceStateUnitTest extends BaseUnitTest {
  @Autowired private WorkspaceDao workspaceDao;

  @Test
  void create_modifyStateWithDifferentFlightId_throwResourceStateConflict() throws Exception {
    Workspace workspace = WorkspaceFixtures.buildMcWorkspace();
    String flightId1 = UUID.randomUUID().toString();
    final String flightId2 = UUID.randomUUID().toString();
    workspaceDao.createWorkspaceStart(workspace, flightId1);
    assertThrows(
        ResourceStateConflictException.class,
        () -> workspaceDao.createWorkspaceSuccess(workspace.workspaceId(), flightId2));

    workspaceDao.createWorkspaceSuccess(workspace.workspaceId(), flightId1);
  }

  @Test
  void delete_modifyStateWithDifferentFlightId_throwResourceStateConflict() throws Exception {
    Workspace workspace = WorkspaceFixtures.buildMcWorkspace();
    String flightId1 = UUID.randomUUID().toString();
    workspaceDao.createWorkspaceStart(workspace, flightId1);
    workspaceDao.createWorkspaceSuccess(workspace.workspaceId(), flightId1);

    String deleteFlightId1 = UUID.randomUUID().toString();
    final String deleteFlightId2 = UUID.randomUUID().toString();

    workspaceDao.deleteWorkspaceStart(workspace.workspaceId(), deleteFlightId1);
    assertThrows(
        ResourceStateConflictException.class,
        () -> workspaceDao.deleteWorkspaceStart(workspace.workspaceId(), deleteFlightId2));

    workspaceDao.deleteWorkspaceSuccess(workspace.workspaceId(), deleteFlightId1);
  }
}
