package bio.terra.workspace.db;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import bio.terra.common.exception.InternalServerErrorException;
import bio.terra.workspace.common.BaseUnitTest;
import bio.terra.workspace.common.logging.model.ActivityLogChangeDetails;
import bio.terra.workspace.common.logging.model.ActivityLogChangedTarget;
import bio.terra.workspace.db.exception.UnknownFlightOperationTypeException;
import bio.terra.workspace.db.model.DbWorkspaceActivityLog;
import bio.terra.workspace.service.workspace.model.OperationType;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

public class WorkspaceActivityLogDaoTest extends BaseUnitTest {

  private static final String USER_EMAIL = "foo@gmail.com";
  private static final String ACTOR_SUBJECT_ID = "foo";

  @Autowired private WorkspaceActivityLogDao activityLogDao;
  @Autowired private NamedParameterJdbcTemplate jdbcTemplate;
  @Autowired private RawDaoTestFixture rawDaoTestFixture;

  @Test
  public void writeActivityAndGet() {
    var workspaceId = UUID.randomUUID();
    assertTrue(activityLogDao.getLastUpdatedDetails(workspaceId).isEmpty());

    activityLogDao.writeActivity(
        workspaceId,
        new DbWorkspaceActivityLog(
            USER_EMAIL,
            ACTOR_SUBJECT_ID,
            OperationType.CREATE,
            workspaceId.toString(),
            ActivityLogChangedTarget.WORKSPACE));

    var lastUpdateDetails = activityLogDao.getLastUpdatedDetails(workspaceId);

    assertTrue(lastUpdateDetails.isPresent());
    assertEquals(USER_EMAIL, lastUpdateDetails.get().actorEmail());
    assertEquals(ACTOR_SUBJECT_ID, lastUpdateDetails.get().actorSubjectId());
    assertEquals(workspaceId.toString(), lastUpdateDetails.get().changeSubjectId());
    assertEquals(ActivityLogChangedTarget.WORKSPACE, lastUpdateDetails.get().changeSubjectType());
  }

  @Test
  public void writeActivity_changeSubjectIsNull() {
    var workspaceId = UUID.randomUUID();

    assertThrows(
        InternalServerErrorException.class,
        () ->
            activityLogDao.writeActivity(
                workspaceId,
                new DbWorkspaceActivityLog(
                    USER_EMAIL,
                    ACTOR_SUBJECT_ID,
                    OperationType.CREATE,
                    null,
                    ActivityLogChangedTarget.FOLDER)));
  }

  @Test
  public void writeActivity_changeSubjectTypeIsNull() {
    UUID workspaceId = UUID.randomUUID();
    assertThrows(
        InternalServerErrorException.class,
        () ->
            activityLogDao.writeActivity(
                workspaceId,
                new DbWorkspaceActivityLog(
                    USER_EMAIL, ACTOR_SUBJECT_ID, OperationType.CREATE, "12345", null)));
  }

  @Test
  public void getLastUpdateDetails() {
    var workspaceId = UUID.randomUUID();
    assertTrue(activityLogDao.getLastUpdatedDetails(workspaceId).isEmpty());

    activityLogDao.writeActivity(
        workspaceId,
        new DbWorkspaceActivityLog(
            USER_EMAIL,
            ACTOR_SUBJECT_ID,
            OperationType.CREATE,
            workspaceId.toString(),
            ActivityLogChangedTarget.WORKSPACE));

    var lastUpdateDetails = activityLogDao.getLastUpdatedDetails(workspaceId);

    var newUserEmail = "foo@gmail.com";
    var newUserSubjectId = "foo";
    activityLogDao.writeActivity(
        workspaceId,
        new DbWorkspaceActivityLog(
            newUserEmail,
            newUserSubjectId,
            OperationType.UPDATE,
            workspaceId.toString(),
            ActivityLogChangedTarget.WORKSPACE));

    var secondLastUpdateDetails = activityLogDao.getLastUpdatedDetails(workspaceId);
    assertEquals(newUserEmail, secondLastUpdateDetails.get().actorEmail());
    assertEquals(newUserSubjectId, secondLastUpdateDetails.get().actorSubjectId());
    var secondLastUpdatedDate = secondLastUpdateDetails.get().changeDate();
    assertTrue(secondLastUpdatedDate.isAfter(lastUpdateDetails.get().changeDate()));
  }

  @Test
  public void getLastUpdatedDate_multipleEntryWithSameTimestamp() {
    var workspaceId = UUID.randomUUID();
    OffsetDateTime now = Instant.now().atOffset(ZoneOffset.UTC);
    rawDaoTestFixture.writeActivityLogWithTimestamp(workspaceId, "anne@gmail.com", now);
    now = Instant.now().atOffset(ZoneOffset.UTC);
    rawDaoTestFixture.writeActivityLogWithTimestamp(workspaceId, "anne@gmail.com", now);
    rawDaoTestFixture.writeActivityLogWithTimestamp(workspaceId, "cathy@gmail.com", now);
    rawDaoTestFixture.writeActivityLogWithTimestamp(workspaceId, "bella@gmail.com", now);

    Optional<ActivityLogChangeDetails> updateDetails =
        activityLogDao.getLastUpdatedDetails(workspaceId);

    assertEquals("anne@gmail.com", updateDetails.get().actorEmail());
    // The two offset date time can have different granularity, resulting flakiness.
    assertTrue(
        now.truncatedTo(ChronoUnit.MILLIS)
            .isEqual(updateDetails.get().changeDate().truncatedTo(ChronoUnit.MILLIS)));
  }

  @Test
  public void getLastUpdatedDate_notUpdateOnUnknownOperationType() {
    var workspaceId = UUID.randomUUID();
    activityLogDao.writeActivity(
        workspaceId,
        new DbWorkspaceActivityLog(
            USER_EMAIL,
            ACTOR_SUBJECT_ID,
            OperationType.CREATE,
            workspaceId.toString(),
            ActivityLogChangedTarget.WORKSPACE));
    var firstUpdateDetails = activityLogDao.getLastUpdatedDetails(workspaceId);
    assertEquals(OperationType.CREATE.name(), getChangeType(workspaceId));

    activityLogDao.writeActivity(
        workspaceId,
        new DbWorkspaceActivityLog(
            USER_EMAIL,
            ACTOR_SUBJECT_ID,
            OperationType.UPDATE,
            workspaceId.toString(),
            ActivityLogChangedTarget.WORKSPACE));
    var secondUpdateDetails = activityLogDao.getLastUpdatedDetails(workspaceId);
    assertEquals(OperationType.UPDATE.name(), getChangeType(workspaceId));

    activityLogDao.writeActivity(
        workspaceId,
        new DbWorkspaceActivityLog(
            USER_EMAIL,
            ACTOR_SUBJECT_ID,
            OperationType.DELETE,
            workspaceId.toString(),
            ActivityLogChangedTarget.WORKSPACE));
    var thirdUpdateDetails = activityLogDao.getLastUpdatedDetails(workspaceId);
    assertEquals(OperationType.DELETE.name(), getChangeType(workspaceId));

    activityLogDao.writeActivity(
        workspaceId,
        new DbWorkspaceActivityLog(
            USER_EMAIL,
            ACTOR_SUBJECT_ID,
            OperationType.CLONE,
            workspaceId.toString(),
            ActivityLogChangedTarget.WORKSPACE));
    var fourthUpdateDetails = activityLogDao.getLastUpdatedDetails(workspaceId);

    assertThrows(
        UnknownFlightOperationTypeException.class,
        () ->
            activityLogDao.writeActivity(
                workspaceId,
                new DbWorkspaceActivityLog(
                    USER_EMAIL,
                    ACTOR_SUBJECT_ID,
                    OperationType.UNKNOWN,
                    workspaceId.toString(),
                    ActivityLogChangedTarget.WORKSPACE)));
    var fifthUpdateDate = activityLogDao.getLastUpdatedDetails(workspaceId);
    assertEquals(fourthUpdateDetails.get().changeDate(), fifthUpdateDate.get().changeDate());

    assertTrue(
        firstUpdateDetails.get().changeDate().isBefore(secondUpdateDetails.get().changeDate()));
    assertTrue(
        secondUpdateDetails.get().changeDate().isBefore(thirdUpdateDetails.get().changeDate()));
    assertTrue(
        thirdUpdateDetails.get().changeDate().isBefore(fourthUpdateDetails.get().changeDate()));
  }

  @Test
  public void getLastUpdatedDate_systemCleanup_filterNonUpdateOperations() {
    var workspaceId = UUID.randomUUID();
    activityLogDao.writeActivity(
        workspaceId,
        new DbWorkspaceActivityLog(
            "bar@gmail.com",
            "bar",
            OperationType.SYSTEM_CLEANUP,
            workspaceId.toString(),
            ActivityLogChangedTarget.WORKSPACE));

    assertEquals(OperationType.SYSTEM_CLEANUP.name(), getChangeType(workspaceId));
    assertTrue(activityLogDao.getLastUpdatedDetails(workspaceId).isEmpty());
  }

  @Test
  public void getLastUpdatedDate_removeWorkspaceRole_filterNonUpdateOperations() {
    var workspaceId = UUID.randomUUID();
    activityLogDao.writeActivity(
        workspaceId,
        new DbWorkspaceActivityLog(
            USER_EMAIL,
            ACTOR_SUBJECT_ID,
            OperationType.REMOVE_WORKSPACE_ROLE,
            "foo@monkeydomonkeysee.com",
            ActivityLogChangedTarget.USER));

    assertEquals(OperationType.REMOVE_WORKSPACE_ROLE.name(), getChangeType(workspaceId));
    assertTrue(activityLogDao.getLastUpdatedDetails(workspaceId).isEmpty());
  }

  @Test
  public void getLastUpdatedDate_grantWorkspaceRole_filterNonUpdateOperations() {
    var workspaceId = UUID.randomUUID();
    activityLogDao.writeActivity(
        workspaceId,
        new DbWorkspaceActivityLog(
            USER_EMAIL,
            ACTOR_SUBJECT_ID,
            OperationType.GRANT_WORKSPACE_ROLE,
            "foo@monkeydomonkeysee.com",
            ActivityLogChangedTarget.USER));

    assertEquals(OperationType.GRANT_WORKSPACE_ROLE.name(), getChangeType(workspaceId));
    assertTrue(activityLogDao.getLastUpdatedDetails(workspaceId).isEmpty());
  }

  @Test
  public void getLastUpdatedDate_emptyTable_getEmpty() {
    assertTrue(activityLogDao.getLastUpdatedDetails(UUID.randomUUID()).isEmpty());
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
