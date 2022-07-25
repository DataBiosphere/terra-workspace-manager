package bio.terra.workspace.db;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import bio.terra.workspace.common.BaseUnitTest;
import bio.terra.workspace.db.exception.UnknownFlightOperationTypeException;
import bio.terra.workspace.db.model.DbWorkspaceActivityLog;
import bio.terra.workspace.service.workspace.model.OperationType;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

public class WorkspaceActivityLogDaoTest extends BaseUnitTest {

  private static final String USER_EMAIL = "foo@gmail.com";
  private static final String SUBJECT_ID = "foo";

  @Autowired WorkspaceActivityLogDao activityLogDao;
  @Autowired private NamedParameterJdbcTemplate jdbcTemplate;

  @Test
  public void setWorkspaceUpdatedDateAndGet() {
    var workspaceId = UUID.randomUUID();
    assertTrue(activityLogDao.getLastUpdatedDate(workspaceId).isEmpty());

    activityLogDao.writeActivity(workspaceId, getDbWorkspaceActivityLog(OperationType.CREATE));

    var latestDate = activityLogDao.getLastUpdatedDate(workspaceId);
    var createdDate = activityLogDao.getCreatedDate(workspaceId);
    var createdBy = activityLogDao.getCreatedBy(workspaceId);
    var lastUpdatedBy = activityLogDao.getLastUpdatedBy(workspaceId);
    assertTrue(latestDate.isPresent());
    assertTrue(createdDate.isPresent());
    assertEquals(latestDate.get(), createdDate.get());
    assertEquals(USER_EMAIL, createdBy.get().getUserEmail());
    assertEquals(SUBJECT_ID, createdBy.get().getUserSubjectId());
    assertEquals(USER_EMAIL, lastUpdatedBy.get().getUserEmail());
    assertEquals(SUBJECT_ID, lastUpdatedBy.get().getUserSubjectId());
  }

  private DbWorkspaceActivityLog getDbWorkspaceActivityLog(OperationType create) {
    return new DbWorkspaceActivityLog()
        .operationType(create)
        .changeAgentEmail(USER_EMAIL)
        .changeAgentSubjectId(SUBJECT_ID);
  }

  @Test
  public void getLastUpdatedDate_notUpdateOnUnknownOperationType() {
    var workspaceId = UUID.randomUUID();
    activityLogDao.writeActivity(workspaceId, getDbWorkspaceActivityLog(OperationType.CREATE));
    var firstUpdatedDate = activityLogDao.getLastUpdatedDate(workspaceId);
    assertEquals(OperationType.CREATE.name(), getChangeType(workspaceId));

    activityLogDao.writeActivity(workspaceId, getDbWorkspaceActivityLog(OperationType.UPDATE));
    var secondUpdatedDate = activityLogDao.getLastUpdatedDate(workspaceId);
    assertEquals(OperationType.UPDATE.name(), getChangeType(workspaceId));

    activityLogDao.writeActivity(workspaceId, getDbWorkspaceActivityLog(OperationType.DELETE));
    var thirdUpdatedDate = activityLogDao.getLastUpdatedDate(workspaceId);
    assertEquals(OperationType.DELETE.name(), getChangeType(workspaceId));

    activityLogDao.writeActivity(workspaceId, getDbWorkspaceActivityLog(OperationType.CLONE));
    var fourthUpdateDate = activityLogDao.getLastUpdatedDate(workspaceId);

    assertThrows(
        UnknownFlightOperationTypeException.class,
        () ->
            activityLogDao.writeActivity(
                workspaceId, getDbWorkspaceActivityLog(OperationType.UNKNOWN)));
    var fifthUpdateDate = activityLogDao.getLastUpdatedDate(workspaceId);
    assertEquals(fourthUpdateDate, fifthUpdateDate);

    assertTrue(firstUpdatedDate.get().isBefore(secondUpdatedDate.get()));
    assertTrue(secondUpdatedDate.get().isBefore(thirdUpdatedDate.get()));
    assertTrue(thirdUpdatedDate.get().isBefore(fourthUpdateDate.get()));

    var createdDate = activityLogDao.getCreatedDate(workspaceId);
    assertEquals(firstUpdatedDate.get(), createdDate.get());
  }

  @Test
  public void getLastUpdatedDate_systemCleanup_filterNonUpdateOperations() {
    var workspaceId = UUID.randomUUID();
    activityLogDao.writeActivity(
        workspaceId,
        getDbWorkspaceActivityLog(OperationType.SYSTEM_CLEANUP)
            .changeAgentEmail("bar@gmail.com")
            .changeAgentSubjectId(null));

    assertEquals(OperationType.SYSTEM_CLEANUP.name(), getChangeType(workspaceId));
    assertTrue(activityLogDao.getLastUpdatedDate(workspaceId).isEmpty());
    assertTrue(activityLogDao.getLastUpdatedBy(workspaceId).isEmpty());
  }

  @Test
  public void getLastUpdatedDate_removeWorkspaceRole_filterNonUpdateOperations() {
    var workspaceId = UUID.randomUUID();
    activityLogDao.writeActivity(
        workspaceId, getDbWorkspaceActivityLog(OperationType.REMOVE_WORKSPACE_ROLE));

    assertEquals(OperationType.REMOVE_WORKSPACE_ROLE.name(), getChangeType(workspaceId));
    assertTrue(activityLogDao.getLastUpdatedDate(workspaceId).isEmpty());
  }

  @Test
  public void getLastUpdatedDate_grantWorkspaceRole_filterNonUpdateOperations() {
    var workspaceId = UUID.randomUUID();
    activityLogDao.writeActivity(
        workspaceId, getDbWorkspaceActivityLog(OperationType.GRANT_WORKSPACE_ROLE));

    assertEquals(OperationType.GRANT_WORKSPACE_ROLE.name(), getChangeType(workspaceId));
    assertTrue(activityLogDao.getLastUpdatedDate(workspaceId).isEmpty());
  }

  @Test
  public void getLastUpdatedDate_emptyTable_getEmpty() {
    assertTrue(activityLogDao.getLastUpdatedDate(UUID.randomUUID()).isEmpty());
  }

  @Test
  public void getCreatedDate_emptyTable_getEmpty() {
    assertTrue(activityLogDao.getCreatedDate(UUID.randomUUID()).isEmpty());
  }

  @Test
  public void getLastUpdatedBy_emptyTable_getEmpty() {
    assertTrue(activityLogDao.getLastUpdatedBy(UUID.randomUUID()).isEmpty());
  }

  @Test
  public void getCreatedByEmail_emptyTable_getEmpty() {
    assertTrue(activityLogDao.getCreatedBy(UUID.randomUUID()).isEmpty());
  }

  private String getChangeType(UUID workspaceId) {
    final String sql =
        "SELECT change_type"
            + " FROM workspace_activity_log WHERE workspace_id = :workspace_id"
            + " ORDER BY change_date DESC LIMIT 1";
    final var params = new MapSqlParameterSource().addValue("workspace_id", workspaceId.toString());
    var changeType = jdbcTemplate.queryForObject(sql, params, String.class);
    return changeType;
  }
}
