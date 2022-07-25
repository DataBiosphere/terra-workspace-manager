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
    assertTrue(activityLogDao.getLastUpdateDetails(workspaceId).isEmpty());

    activityLogDao.writeActivity(workspaceId, getDbWorkspaceActivityLog(OperationType.CREATE));

    var lastUpdateDetails = activityLogDao.getLastUpdateDetails(workspaceId);
    var createDetails = activityLogDao.getCreateDetails(workspaceId);

    assertTrue(lastUpdateDetails.isPresent());
    assertTrue(createDetails.isPresent());
    assertEquals(lastUpdateDetails.get().getDateTime(), createDetails.get().getDateTime());
    assertEquals(USER_EMAIL, createDetails.get().getUserEmail());
    assertEquals(SUBJECT_ID, createDetails.get().getSubjectId());
    assertEquals(USER_EMAIL, lastUpdateDetails.get().getUserEmail());
    assertEquals(SUBJECT_ID, lastUpdateDetails.get().getSubjectId());
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
    var firstUpdateDetails = activityLogDao.getLastUpdateDetails(workspaceId);
    assertEquals(OperationType.CREATE.name(), getChangeType(workspaceId));

    activityLogDao.writeActivity(workspaceId, getDbWorkspaceActivityLog(OperationType.UPDATE));
    var secondUpdateDetails = activityLogDao.getLastUpdateDetails(workspaceId);
    assertEquals(OperationType.UPDATE.name(), getChangeType(workspaceId));

    activityLogDao.writeActivity(workspaceId, getDbWorkspaceActivityLog(OperationType.DELETE));
    var thirdUpdateDetails = activityLogDao.getLastUpdateDetails(workspaceId);
    assertEquals(OperationType.DELETE.name(), getChangeType(workspaceId));

    activityLogDao.writeActivity(workspaceId, getDbWorkspaceActivityLog(OperationType.CLONE));
    var fourthUpdateDetails = activityLogDao.getLastUpdateDetails(workspaceId);

    assertThrows(
        UnknownFlightOperationTypeException.class,
        () ->
            activityLogDao.writeActivity(
                workspaceId, getDbWorkspaceActivityLog(OperationType.UNKNOWN)));
    var fifthUpdateDate = activityLogDao.getLastUpdateDetails(workspaceId);
    assertEquals(fourthUpdateDetails.get().getDateTime(), fifthUpdateDate.get().getDateTime());

    assertTrue(
        firstUpdateDetails.get().getDateTime().isBefore(secondUpdateDetails.get().getDateTime()));
    assertTrue(
        secondUpdateDetails.get().getDateTime().isBefore(thirdUpdateDetails.get().getDateTime()));
    assertTrue(
        thirdUpdateDetails.get().getDateTime().isBefore(fourthUpdateDetails.get().getDateTime()));

    var createDetails = activityLogDao.getCreateDetails(workspaceId);
    assertEquals(firstUpdateDetails.get().getDateTime(), createDetails.get().getDateTime());
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
    assertTrue(activityLogDao.getLastUpdateDetails(workspaceId).isEmpty());
  }

  @Test
  public void getLastUpdatedDate_removeWorkspaceRole_filterNonUpdateOperations() {
    var workspaceId = UUID.randomUUID();
    activityLogDao.writeActivity(
        workspaceId, getDbWorkspaceActivityLog(OperationType.REMOVE_WORKSPACE_ROLE));

    assertEquals(OperationType.REMOVE_WORKSPACE_ROLE.name(), getChangeType(workspaceId));
    assertTrue(activityLogDao.getLastUpdateDetails(workspaceId).isEmpty());
  }

  @Test
  public void getLastUpdatedDate_grantWorkspaceRole_filterNonUpdateOperations() {
    var workspaceId = UUID.randomUUID();
    activityLogDao.writeActivity(
        workspaceId, getDbWorkspaceActivityLog(OperationType.GRANT_WORKSPACE_ROLE));

    assertEquals(OperationType.GRANT_WORKSPACE_ROLE.name(), getChangeType(workspaceId));
    assertTrue(activityLogDao.getLastUpdateDetails(workspaceId).isEmpty());
  }

  @Test
  public void getLastUpdatedDate_emptyTable_getEmpty() {
    assertTrue(activityLogDao.getLastUpdateDetails(UUID.randomUUID()).isEmpty());
  }

  @Test
  public void getCreatedDate_emptyTable_getEmpty() {
    assertTrue(activityLogDao.getCreateDetails(UUID.randomUUID()).isEmpty());
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
