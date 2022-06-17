package bio.terra.workspace.db;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import bio.terra.workspace.common.BaseUnitTest;
import bio.terra.workspace.db.model.ActivityLogChangedType;
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
    String workspaceId = UUID.randomUUID().toString();
    assertNull(activityLogDao.getLastChangedDate(workspaceId));

    activityLogDao.setChangedDate(workspaceId, ActivityLogChangedType.CREATE);

    String changeType = getChangeType(workspaceId);
    var latestDate = activityLogDao.getLastChangedDate(workspaceId);
    assertNotNull(latestDate);
    assertEquals(ActivityLogChangedType.CREATE.name(), changeType);
  }

  private String getChangeType(String workspaceId) {
    final String sql =
        "SELECT change_type"
            + " FROM activity_log WHERE workspace_id = :workspace_id"
            + " ORDER BY change_date DESC LIMIT 1";
    final var params =
        new MapSqlParameterSource()
            .addValue("workspace_id", workspaceId)
            .addValue("change_type", ActivityLogChangedType.CREATE.name());
    var changeType = jdbcTemplate.queryForObject(sql, params, String.class);
    return changeType;
  }

  @Test
  public void getLatestDate() {
    String workspaceId = UUID.randomUUID().toString();
    activityLogDao.setChangedDate(workspaceId, ActivityLogChangedType.CREATE);
    var firstUpdatedDate = activityLogDao.getLastChangedDate(workspaceId);
    assertEquals(ActivityLogChangedType.CREATE.name(), getChangeType(workspaceId));

    activityLogDao.setChangedDate(workspaceId, ActivityLogChangedType.UPDATE);
    var secondUpdatedDate = activityLogDao.getLastChangedDate(workspaceId);
    assertEquals(ActivityLogChangedType.UPDATE.name(), getChangeType(workspaceId));

    activityLogDao.setChangedDate(workspaceId, ActivityLogChangedType.DELETE);
    var thirdUpdatedDate = activityLogDao.getLastChangedDate(workspaceId);
    assertEquals(ActivityLogChangedType.DELETE.name(), getChangeType(workspaceId));

    assertTrue(firstUpdatedDate.isBefore(secondUpdatedDate));
    assertTrue(secondUpdatedDate.isBefore(thirdUpdatedDate));
  }
}
