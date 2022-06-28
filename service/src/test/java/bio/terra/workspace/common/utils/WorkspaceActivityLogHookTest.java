package bio.terra.workspace.common.utils;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import bio.terra.common.exception.MissingRequiredFieldException;
import bio.terra.stairway.FlightDebugInfo;
import bio.terra.workspace.common.BaseUnitTest;
import bio.terra.workspace.db.WorkspaceActivityLogDao;
import bio.terra.workspace.db.WorkspaceDao;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.job.JobService;
import bio.terra.workspace.service.job.JobServiceTestFlight;
import bio.terra.workspace.service.workspace.model.OperationType;
import bio.terra.workspace.service.workspace.model.Workspace;
import bio.terra.workspace.service.workspace.model.WorkspaceStage;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

public class WorkspaceActivityLogHookTest extends BaseUnitTest {
  private static final AuthenticatedUserRequest testUser =
      new AuthenticatedUserRequest()
          .subjectId("StairwayUnit")
          .email("stairway@unit.com")
          .token(Optional.of("not-a-real-token"));

  @Autowired private JobService jobService;
  @Autowired private WorkspaceDao workspaceDao;
  @Autowired private WorkspaceActivityLogDao activityLogDao;
  @MockBean private SamService mockSamService;

  /**
   * Reset the {@link JobService} {@link FlightDebugInfo} after each test so that future submissions
   * aren't affected.
   */
  @AfterEach
  void clearFlightDebugInfo() {
    jobService.setFlightDebugInfoForTest(null);
  }

  @Test
  void createFlightSucceeds_activityLogUpdated() {
    UUID workspaceUuid = UUID.randomUUID();
    var emptyChangedDate = activityLogDao.getLastUpdatedDate(workspaceUuid);
    assertTrue(emptyChangedDate.isEmpty());

    runFlight("a creation flight", workspaceUuid, OperationType.CREATE);
    var changedDate = activityLogDao.getLastUpdatedDate(workspaceUuid);
    assertTrue(changedDate.isPresent());
  }

  @Test
  void deleteFlightSucceeds_activityLogUpdated() {
    UUID workspaceUuid = UUID.randomUUID();
    var emptyChangedDate = activityLogDao.getLastUpdatedDate(workspaceUuid);
    assertTrue(emptyChangedDate.isEmpty());

    runFlight("a creation flight", workspaceUuid, OperationType.DELETE);

    var changedDate = activityLogDao.getLastUpdatedDate(workspaceUuid);
    assertTrue(changedDate.isPresent());
  }

  @Test
  void unknownFlightSucceeds_activityLogNotUpdated() {
    UUID workspaceUuid = UUID.randomUUID();
    var emptyChangedDate = activityLogDao.getLastUpdatedDate(workspaceUuid);
    assertTrue(emptyChangedDate.isEmpty());

    assertThrows(
        MissingRequiredFieldException.class,
        () -> runFlight("a creation flight", workspaceUuid, OperationType.UNKNOWN));

    var changedDate = activityLogDao.getLastUpdatedDate(workspaceUuid);
    assertTrue(changedDate.isEmpty());
  }

  @Test
  void createFlightFails_activityLogNotUpdated() {
    // Set a FlightDebugInfo so that any job submission should fail on the last step.
    jobService.setFlightDebugInfoForTest(
        FlightDebugInfo.newBuilder().lastStepFailure(true).build());
    UUID workspaceUuid = UUID.randomUUID();
    var emptyChangedDate = activityLogDao.getLastUpdatedDate(workspaceUuid);
    assertTrue(emptyChangedDate.isEmpty());

    runFlight("Failed flight with operation type CREATE", workspaceUuid, OperationType.CREATE);

    var changedDateAfterFailedFlight = activityLogDao.getLastUpdatedDate(workspaceUuid);
    assertTrue(changedDateAfterFailedFlight.isEmpty());
  }

  @Test
  void deleteFlightFails_resourceNotDeleted_activityLogNotUpdated() {
    // Set a FlightDebugInfo so that any job submission should fail on the last step.
    jobService.setFlightDebugInfoForTest(
        FlightDebugInfo.newBuilder().lastStepFailure(true).build());
    UUID workspaceUuid = UUID.randomUUID();
    var emptyChangedDate = activityLogDao.getLastUpdatedDate(workspaceUuid);
    assertTrue(emptyChangedDate.isEmpty());

    runFlight("unhandled deletion flight", workspaceUuid, OperationType.DELETE);

    var changedDateAfterFailedFlight = activityLogDao.getLastUpdatedDate(workspaceUuid);
    assertTrue(changedDateAfterFailedFlight.isEmpty());
  }

  @Test
  void unknownFlightFails_activityLogNotUpdated() {
    // Set a FlightDebugInfo so that any job submission should fail on the last step.
    jobService.setFlightDebugInfoForTest(
        FlightDebugInfo.newBuilder().lastStepFailure(true).build());
    UUID workspaceUuid = UUID.randomUUID();
    var emptyChangedDate = activityLogDao.getLastUpdatedDate(workspaceUuid);
    assertTrue(emptyChangedDate.isEmpty());

    assertThrows(
        MissingRequiredFieldException.class,
        () ->
            runFlight(
                "failed flight with operation type unknown", workspaceUuid, OperationType.UNKNOWN));

    var changedDateAfterFailedFlight = activityLogDao.getLastUpdatedDate(workspaceUuid);
    assertTrue(changedDateAfterFailedFlight.isEmpty());
  }

  // Submit a flight; wait for it to finish; return the flight id
  // Use the jobId defaulting in the JobBuilder
  private String runFlight(String description, UUID workspaceUuid, OperationType operationType) {
    Workspace workspace =
        Workspace.builder()
            .workspaceId(workspaceUuid)
            .userFacingId("a" + workspaceUuid)
            .workspaceStage(WorkspaceStage.MC_WORKSPACE)
            .description(description)
            .build();
    workspaceDao.createWorkspace(workspace);
    String jobId =
        jobService
            .newJob()
            .description(description)
            .flightClass(JobServiceTestFlight.class)
            .userRequest(testUser)
            .workspaceId(workspaceUuid.toString())
            .operationType(operationType)
            .submit();
    jobService.waitForJob(jobId);
    return jobId;
  }
}
