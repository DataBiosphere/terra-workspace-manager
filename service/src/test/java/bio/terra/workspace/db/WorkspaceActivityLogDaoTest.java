package bio.terra.workspace.db;

import static bio.terra.workspace.db.model.DbWorkspaceActivityLog.getDbWorkspaceActivityLog;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import bio.terra.workspace.common.BaseUnitTest;
import bio.terra.workspace.db.exception.UnknownFlightOperationTypeException;
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
  public void writeActivityAndGet() {
    var workspaceId = UUID.randomUUID();
    assertTrue(activityLogDao.getLastUpdateDetails(workspaceId).isEmpty());

    activityLogDao.writeActivity(
        workspaceId, getDbWorkspaceActivityLog(OperationType.CREATE, USER_EMAIL, SUBJECT_ID));

    var lastUpdateDetails = activityLogDao.getLastUpdateDetails(workspaceId);
    var createDetails = activityLogDao.getCreateDetails(workspaceId);

    assertTrue(lastUpdateDetails.isPresent());
    assertTrue(createDetails.isPresent());
    assertEquals(lastUpdateDetails.get().getChangeDate(), createDetails.get().getChangeDate());
    assertEquals(USER_EMAIL, createDetails.get().getActorEmail());
    assertEquals(SUBJECT_ID, createDetails.get().getActorSubjectId());
    assertEquals(USER_EMAIL, lastUpdateDetails.get().getActorEmail());
    assertEquals(SUBJECT_ID, lastUpdateDetails.get().getActorSubjectId());
  }

  @Test
  public void getLastUpdateDetails() {
    var workspaceId = UUID.randomUUID();
    assertTrue(activityLogDao.getLastUpdateDetails(workspaceId).isEmpty());

    activityLogDao.writeActivity(
        workspaceId, getDbWorkspaceActivityLog(OperationType.CREATE, USER_EMAIL, SUBJECT_ID));

    var lastUpdateDetails = activityLogDao.getLastUpdateDetails(workspaceId);

    var newUserEmail = "foo@gmail.com";
    var newUserSubjectId = "foo";
    activityLogDao.writeActivity(
        workspaceId,
        getDbWorkspaceActivityLog(OperationType.UPDATE, newUserEmail, newUserSubjectId));

    var secondLastUpdateDetails = activityLogDao.getLastUpdateDetails(workspaceId);
    assertEquals(newUserEmail, secondLastUpdateDetails.get().getActorEmail());
    assertEquals(newUserSubjectId, secondLastUpdateDetails.get().getActorSubjectId());
    var secondLastUpdatedDate = secondLastUpdateDetails.get().getChangeDate();
    assertTrue(secondLastUpdatedDate.isAfter(lastUpdateDetails.get().getChangeDate()));
  }

  @Test
  public void getCreateDetails() {
    var workspaceId = UUID.randomUUID();
    assertTrue(activityLogDao.getCreateDetails(workspaceId).isEmpty());

    activityLogDao.writeActivity(
        workspaceId, getDbWorkspaceActivityLog(OperationType.CREATE, USER_EMAIL, SUBJECT_ID));

    var createDetails = activityLogDao.getCreateDetails(workspaceId);
    assertEquals(USER_EMAIL, createDetails.get().getActorEmail());
    assertEquals(SUBJECT_ID, createDetails.get().getActorSubjectId());

    var newUserEmail = "foo@gmail.com";
    var subjectId = "foo";
    activityLogDao.writeActivity(
        workspaceId, getDbWorkspaceActivityLog(OperationType.UPDATE, newUserEmail, subjectId));

    var createDetailsAfterUpdate = activityLogDao.getCreateDetails(workspaceId);
    assertEquals(USER_EMAIL, createDetailsAfterUpdate.get().getActorEmail());
    assertEquals(SUBJECT_ID, createDetailsAfterUpdate.get().getActorSubjectId());
  }

  @Test
  public void getLastUpdatedDate_notUpdateOnUnknownOperationType() {
    var workspaceId = UUID.randomUUID();
    activityLogDao.writeActivity(
        workspaceId, getDbWorkspaceActivityLog(OperationType.CREATE, USER_EMAIL, SUBJECT_ID));
    var firstUpdateDetails = activityLogDao.getLastUpdateDetails(workspaceId);
    assertEquals(OperationType.CREATE.name(), getChangeType(workspaceId));

    activityLogDao.writeActivity(
        workspaceId, getDbWorkspaceActivityLog(OperationType.UPDATE, USER_EMAIL, SUBJECT_ID));
    var secondUpdateDetails = activityLogDao.getLastUpdateDetails(workspaceId);
    assertEquals(OperationType.UPDATE.name(), getChangeType(workspaceId));

    activityLogDao.writeActivity(
        workspaceId, getDbWorkspaceActivityLog(OperationType.DELETE, USER_EMAIL, SUBJECT_ID));
    var thirdUpdateDetails = activityLogDao.getLastUpdateDetails(workspaceId);
    assertEquals(OperationType.DELETE.name(), getChangeType(workspaceId));

    activityLogDao.writeActivity(
        workspaceId, getDbWorkspaceActivityLog(OperationType.CLONE, USER_EMAIL, SUBJECT_ID));
    var fourthUpdateDetails = activityLogDao.getLastUpdateDetails(workspaceId);

    assertThrows(
        UnknownFlightOperationTypeException.class,
        () ->
            activityLogDao.writeActivity(
                workspaceId,
                getDbWorkspaceActivityLog(OperationType.UNKNOWN, USER_EMAIL, SUBJECT_ID)));
    var fifthUpdateDate = activityLogDao.getLastUpdateDetails(workspaceId);
    assertEquals(fourthUpdateDetails.get().getChangeDate(), fifthUpdateDate.get().getChangeDate());

    assertTrue(
        firstUpdateDetails
            .get()
            .getChangeDate()
            .isBefore(secondUpdateDetails.get().getChangeDate()));
    assertTrue(
        secondUpdateDetails
            .get()
            .getChangeDate()
            .isBefore(thirdUpdateDetails.get().getChangeDate()));
    assertTrue(
        thirdUpdateDetails
            .get()
            .getChangeDate()
            .isBefore(fourthUpdateDetails.get().getChangeDate()));

    var createDetails = activityLogDao.getCreateDetails(workspaceId);
    assertEquals(firstUpdateDetails.get().getChangeDate(), createDetails.get().getChangeDate());
  }

  @Test
  public void getLastUpdatedDate_systemCleanup_filterNonUpdateOperations() {
    var workspaceId = UUID.randomUUID();
    activityLogDao.writeActivity(
        workspaceId,
        getDbWorkspaceActivityLog(OperationType.SYSTEM_CLEANUP, "bar@gmail.com", "bar"));

    assertEquals(OperationType.SYSTEM_CLEANUP.name(), getChangeType(workspaceId));
    assertTrue(activityLogDao.getLastUpdateDetails(workspaceId).isEmpty());
  }

  @Test
  public void getLastUpdatedDate_removeWorkspaceRole_filterNonUpdateOperations() {
    var workspaceId = UUID.randomUUID();
    activityLogDao.writeActivity(
        workspaceId,
        getDbWorkspaceActivityLog(OperationType.REMOVE_WORKSPACE_ROLE, USER_EMAIL, SUBJECT_ID));

    assertEquals(OperationType.REMOVE_WORKSPACE_ROLE.name(), getChangeType(workspaceId));
    assertTrue(activityLogDao.getLastUpdateDetails(workspaceId).isEmpty());
  }

  @Test
  public void getLastUpdatedDate_grantWorkspaceRole_filterNonUpdateOperations() {
    var workspaceId = UUID.randomUUID();
    activityLogDao.writeActivity(
        workspaceId,
        getDbWorkspaceActivityLog(OperationType.GRANT_WORKSPACE_ROLE, USER_EMAIL, SUBJECT_ID));

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
