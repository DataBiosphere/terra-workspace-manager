package bio.terra.workspace.db;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import bio.terra.workspace.common.BaseUnitTest;
import bio.terra.workspace.db.model.DbActivityLog;
import bio.terra.workspace.service.workspace.model.OperationType;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

public class ActivityLogDaoTest extends BaseUnitTest {

  @Autowired ActivityLogDao activityLogDao;
  @Autowired private NamedParameterJdbcTemplate jdbcTemplate;

  @Test
  public void setWorkspaceUpdatedDateAndGet() {
    var workspaceId = UUID.randomUUID();
    assertNull(activityLogDao.getLastChangedDate(workspaceId));

    activityLogDao.writeActivity(
        workspaceId, new DbActivityLog().operationType(OperationType.CREATE));

    String changeType = getChangedType(workspaceId);
    var latestDate = activityLogDao.getLastChangedDate(workspaceId);
    assertNotNull(latestDate);
    assertEquals(OperationType.CREATE.name(), changeType);
  }

  @Test
  public void getLastUpdatedDate() {
    var workspaceId = UUID.randomUUID();
    activityLogDao.writeActivity(
        workspaceId, new DbActivityLog().operationType(OperationType.CREATE));
    var firstUpdatedDate = activityLogDao.getLastChangedDate(workspaceId);
    assertEquals(OperationType.CREATE.name(), getChangedType(workspaceId));

    activityLogDao.writeActivity(
        workspaceId, new DbActivityLog().operationType(OperationType.UPDATE));
    var secondUpdatedDate = activityLogDao.getLastChangedDate(workspaceId);
    assertEquals(OperationType.UPDATE.name(), getChangedType(workspaceId));

    activityLogDao.writeActivity(
        workspaceId, new DbActivityLog().operationType(OperationType.DELETE));
    var thirdUpdatedDate = activityLogDao.getLastChangedDate(workspaceId);
    assertEquals(OperationType.DELETE.name(), getChangedType(workspaceId));

    activityLogDao.writeActivity(
        workspaceId, new DbActivityLog().operationType(OperationType.CLONE));
    var fourthUpdateDate = activityLogDao.getLastChangedDate(workspaceId);
    assertEquals(thirdUpdatedDate, fourthUpdateDate);

    activityLogDao.writeActivity(
        workspaceId, new DbActivityLog().operationType(OperationType.UNKNOWN));
    var fifthUpdateDate = activityLogDao.getLastChangedDate(workspaceId);
    assertEquals(thirdUpdatedDate, fifthUpdateDate);

    assertTrue(firstUpdatedDate.isBefore(secondUpdatedDate));
    assertTrue(secondUpdatedDate.isBefore(thirdUpdatedDate));
  }

  private String getChangedType(UUID workspaceId) {
    final String sql =
        "SELECT change_type"
            + " FROM workspace_activity_log WHERE workspace_id = :workspace_id"
            + " ORDER BY changed_date DESC LIMIT 1";
    final var params = new MapSqlParameterSource().addValue("workspace_id", workspaceId.toString());
    var changeType = jdbcTemplate.queryForObject(sql, params, String.class);
    return changeType;
  }
}
