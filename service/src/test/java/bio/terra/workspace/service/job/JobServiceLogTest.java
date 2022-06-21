package bio.terra.workspace.service.job;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;

import bio.terra.stairway.FlightDebugInfo;
import bio.terra.workspace.common.BaseUnitTest;
import bio.terra.workspace.db.WorkspaceActivityLogDao;
import bio.terra.workspace.db.WorkspaceDao;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.workspace.model.OperationType;
import bio.terra.workspace.service.workspace.model.Workspace;
import bio.terra.workspace.service.workspace.model.WorkspaceStage;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

public class JobServiceLogTest extends BaseUnitTest {
  private static final AuthenticatedUserRequest testUser =
      new AuthenticatedUserRequest()
          .subjectId("StairwayUnit")
          .email("stairway@unit.com")
          .token(Optional.of("not-a-real-token"));

  @Autowired private JobService jobService;
  @Autowired private WorkspaceDao workspaceDao;
  @Autowired private WorkspaceActivityLogDao activityLogDao;
  @MockBean private SamService mockSamService;

  @BeforeEach
  @SuppressFBWarnings(value = "DE_MIGHT_IGNORE", justification = "Mockito flakiness")
  void setup() {
    try {
      Mockito.doReturn(true).when(mockSamService.isAuthorized(any(), any(), any(), any()));
    } catch (Exception e) {
      // How does a mock even throw an exception?
    }
  }

  /**
   * Reset the {@link JobService} {@link FlightDebugInfo} after each test so that future submissions
   * aren't affected.
   */
  @AfterEach
  void clearFlightDebugInfo() {
    jobService.setFlightDebugInfoForTest(null);
  }

  @Test
  void setChangedActivityWhenFlightCreateComplete() {
    UUID workspaceUuid = UUID.randomUUID();
    var emptyChangedDate = activityLogDao.getLastUpdatedDate(workspaceUuid);
    assertTrue(emptyChangedDate.isEmpty());

    runFlight("a creation flight", workspaceUuid, OperationType.CREATE);
    var changedDate = activityLogDao.getLastUpdatedDate(workspaceUuid);
    assertTrue(changedDate.isPresent());
  }

  @Test
  void setChangedActivityWhenFlightUpdateComplete() {
    UUID workspaceUuid = UUID.randomUUID();
    var emptyChangedDate = activityLogDao.getLastUpdatedDate(workspaceUuid);
    assertTrue(emptyChangedDate.isEmpty());

    runFlight("a creation flight", workspaceUuid, OperationType.UPDATE);
    var changedDate = activityLogDao.getLastUpdatedDate(workspaceUuid);
    assertTrue(changedDate.isPresent());
  }

  @Test
  void setChangedActivityWhenFlightDeleteComplete() {
    UUID workspaceUuid = UUID.randomUUID();
    var emptyChangedDate = activityLogDao.getLastUpdatedDate(workspaceUuid);
    assertTrue(emptyChangedDate.isEmpty());

    runFlight("a creation flight", workspaceUuid, OperationType.DELETE);
    var changedDate = activityLogDao.getLastUpdatedDate(workspaceUuid);
    assertTrue(changedDate.isPresent());
  }

  @Test
  void setChangedActivityWhenFlightCloneComplete_notSet() {
    UUID workspaceUuid = UUID.randomUUID();
    var emptyChangedDate = activityLogDao.getLastUpdatedDate(workspaceUuid);
    assertTrue(emptyChangedDate.isEmpty());

    runFlight("a creation flight", workspaceUuid, OperationType.CLONE);
    var changedDate = activityLogDao.getLastUpdatedDate(workspaceUuid);
    assertTrue(changedDate.isPresent());
  }

  @Test
  void setChangedActivityWhenFlightUnknownComplete_notSet() {
    UUID workspaceUuid = UUID.randomUUID();
    var emptyChangedDate = activityLogDao.getLastUpdatedDate(workspaceUuid);
    assertTrue(emptyChangedDate.isEmpty());

    runFlight("a creation flight", workspaceUuid, OperationType.UNKNOWN);
    var changedDate = activityLogDao.getLastUpdatedDate(workspaceUuid);
    assertTrue(changedDate.isEmpty());
  }

  @Test
  void setChangedActivityWhenFlightCreateFails_notSet() {
    // Set a FlightDebugInfo so that any job submission should fail on the last step.
    jobService.setFlightDebugInfoForTest(
        FlightDebugInfo.newBuilder().lastStepFailure(true).build());
    UUID workspaceUuid = UUID.randomUUID();
    var emptyChangedDate = activityLogDao.getLastUpdatedDate(workspaceUuid);
    assertTrue(emptyChangedDate.isEmpty());

    var description = "fail for FlightDebugInfo";
    runFlight(description, workspaceUuid, OperationType.CREATE);
    var changedDateAfterFailedFlight = activityLogDao.getLastUpdatedDate(workspaceUuid);
    assertTrue(changedDateAfterFailedFlight.isEmpty());
  }

  @Test
  void setChangedActivityWhenFlightUpdateFails_notSet() {
    // Set a FlightDebugInfo so that any job submission should fail on the last step.
    jobService.setFlightDebugInfoForTest(
        FlightDebugInfo.newBuilder().lastStepFailure(true).build());
    UUID workspaceUuid = UUID.randomUUID();
    var emptyChangedDate = activityLogDao.getLastUpdatedDate(workspaceUuid);
    assertTrue(emptyChangedDate.isEmpty());

    runFlight("failed flight with operation type UPDATE", workspaceUuid, OperationType.UPDATE);
    var changedDateAfterFailedFlight = activityLogDao.getLastUpdatedDate(workspaceUuid);
    assertTrue(changedDateAfterFailedFlight.isEmpty());
  }

  @Test
  void setChangedActivityWhenFlightCloneFails_notSet() {
    // Set a FlightDebugInfo so that any job submission should fail on the last step.
    jobService.setFlightDebugInfoForTest(
        FlightDebugInfo.newBuilder().lastStepFailure(true).build());
    UUID workspaceUuid = UUID.randomUUID();
    var emptyChangedDate = activityLogDao.getLastUpdatedDate(workspaceUuid);
    assertTrue(emptyChangedDate.isEmpty());

    runFlight("failed flight with operation type CLONE", workspaceUuid, OperationType.CLONE);
    var changedDateAfterFailedFlight = activityLogDao.getLastUpdatedDate(workspaceUuid);
    assertTrue(changedDateAfterFailedFlight.isEmpty());
  }

  @Test
  void setChangedActivityWhenFlightUnknownFails_notSet() {
    // Set a FlightDebugInfo so that any job submission should fail on the last step.
    jobService.setFlightDebugInfoForTest(
        FlightDebugInfo.newBuilder().lastStepFailure(true).build());
    UUID workspaceUuid = UUID.randomUUID();
    var emptyChangedDate = activityLogDao.getLastUpdatedDate(workspaceUuid);
    assertTrue(emptyChangedDate.isEmpty());

    runFlight("failed flight with operation type unkown", workspaceUuid, OperationType.UNKNOWN);
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
