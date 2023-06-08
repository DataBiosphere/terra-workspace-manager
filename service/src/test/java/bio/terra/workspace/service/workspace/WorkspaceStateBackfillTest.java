package bio.terra.workspace.service.workspace;

import static bio.terra.workspace.common.testfixtures.WorkspaceFixtures.createDefaultMcWorkspace;
import static bio.terra.workspace.common.testfixtures.WorkspaceFixtures.createWorkspaceInDb;

import bio.terra.workspace.common.BaseUnitTest;
import bio.terra.workspace.db.WorkspaceDao;
import bio.terra.workspace.db.model.DbWorkspace;
import bio.terra.workspace.service.resource.model.WsmResourceState;
import java.util.UUID;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

public class WorkspaceStateBackfillTest extends BaseUnitTest {
  @Autowired WorkspaceDao workspaceDao;
  @Autowired NamedParameterJdbcTemplate jdbcTemplate;

  @Test
  public void testBackfillQuery() {
    var workspace = createDefaultMcWorkspace();
    UUID workspaceId = workspace.workspaceId();
    createWorkspaceInDb(workspace, workspaceDao);

    jdbcTemplate.update("UPDATE workspace SET state = NULL", new MapSqlParameterSource());

    DbWorkspace dbWorkspace = workspaceDao.getDbWorkspace(workspaceId);
    Assertions.assertNull(dbWorkspace.getState());

    workspaceDao.backfillWorkspaceState();

    dbWorkspace = workspaceDao.getDbWorkspace(workspaceId);
    Assertions.assertEquals(WsmResourceState.READY, dbWorkspace.getState());
  }
}
