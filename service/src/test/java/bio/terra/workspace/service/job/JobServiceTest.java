package bio.terra.workspace.service.job;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import bio.terra.common.exception.MissingRequiredFieldException;
import bio.terra.stairway.FlightDebugInfo;
import bio.terra.workspace.common.MockBeanUnitTest;
import bio.terra.workspace.db.WorkspaceDao;
import bio.terra.workspace.generated.model.ApiJobReport;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.job.exception.InvalidJobIdException;
import bio.terra.workspace.service.job.exception.InvalidResultStateException;
import bio.terra.workspace.service.job.exception.JobNotFoundException;
import bio.terra.workspace.service.workspace.model.OperationType;
import bio.terra.workspace.service.workspace.model.Workspace;
import bio.terra.workspace.service.workspace.model.WorkspaceStage;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.DirtiesContext.MethodMode;

class JobServiceTest extends MockBeanUnitTest {
  private final AuthenticatedUserRequest testUser =
      new AuthenticatedUserRequest()
          .subjectId("StairwayUnit")
          .email("stairway@unit.com")
          .token(Optional.of("not-a-real-token"));

  @Autowired private JobService jobService;
  @Autowired private WorkspaceDao workspaceDao;

  /**
   * Reset the {@link JobService} {@link FlightDebugInfo} after each test so that future submissions
   * aren't affected.
   */
  @AfterEach
  void clearFlightDebugInfo() {
    jobService.setFlightDebugInfoForTest(null);
  }

  @Test
  void emptyStringJobIdTest() {
    String testJobId = "";
    assertThrows(
        InvalidJobIdException.class,
        () ->
            jobService
                .newJob()
                .description("description")
                .jobId(testJobId)
                .flightClass(JobServiceTestFlight.class)
                .workspaceId(UUID.randomUUID().toString())
                .userRequest(testUser)
                .operationType(OperationType.DELETE));
  }

  @Test
  void whitespaceStringJobIdTest() {
    String testJobId = "\t ";
    assertThrows(
        InvalidJobIdException.class,
        () ->
            jobService
                .newJob()
                .description("description")
                .jobId(testJobId)
                .flightClass(JobServiceTestFlight.class)
                .userRequest(testUser)
                .workspaceId(UUID.randomUUID().toString())
                .operationType(OperationType.DELETE));
  }

  @Test
  void unknownJobOperationType() {
    assertThrows(
        MissingRequiredFieldException.class,
        () ->
            jobService
                .newJob()
                .description("description")
                .jobId("test-job-id")
                .flightClass(JobServiceTestFlight.class)
                .userRequest(testUser)
                .workspaceId(UUID.randomUUID().toString())
                .submit());
  }

  // Resets the application context before retrieveTest to make sure that the job service does not
  // have some failed jobs left over from other tests.
  @DirtiesContext(methodMode = MethodMode.BEFORE_METHOD)
  @Test
  void retrieveTest() throws Exception {
    // We perform 7 flights and then retrieve and enumerate them.
    // The fids list should be in exactly the same order as the database ordered by submit time.

    List<String> jobIds = new ArrayList<>();
    for (int i = 0; i < 7; i++) {
      String jobId = runFlight(makeDescription(i));
      jobIds.add(jobId);
    }

    // Test single retrieval
    testSingleRetrieval(jobIds);

    // Test result retrieval - the body should be the description string
    testResultRetrieval(jobIds);

    // Retrieve everything
    testEnumRange(jobIds, 0, 100);

    // Retrieve the middle 3; offset means skip 2 rows
    testEnumRange(jobIds, 2, 3);

    // Retrieve from the end; should only get the last one back
    testEnumCount(1, 6, 3);

    // Retrieve past the end; should get nothing
    testEnumCount(0, 22, 3);
  }

  private void testSingleRetrieval(List<String> fids) {
    ApiJobReport response = jobService.retrieveJob(fids.get(2));
    assertThat(response, notNullValue());
    validateJobReport(response, 2, fids);
  }

  private void testResultRetrieval(List<String> fids) {
    JobService.JobResultOrException<String> resultHolder =
        jobService.retrieveJobResult(fids.get(2), String.class);

    assertNull(resultHolder.getException());
    assertThat(resultHolder.getResult(), equalTo(makeDescription(2)));
  }

  // Get some range and compare it with the fids
  private void testEnumRange(List<String> fids, int offset, int limit) {
    List<ApiJobReport> jobList = jobService.enumerateJobs(offset, limit, testUser);
    assertThat(jobList, notNullValue());
    int index = offset;
    for (ApiJobReport job : jobList) {
      validateJobReport(job, index, fids);
      index++;
    }
  }

  // Get some range and make sure we got the number we expected
  private void testEnumCount(int count, int offset, int length) {
    List<ApiJobReport> jobList = jobService.enumerateJobs(offset, length, testUser);
    assertThat(jobList, notNullValue());
    assertThat(jobList.size(), equalTo(count));
  }

  @Test
  void testBadIdRetrieveJob() {
    assertThrows(JobNotFoundException.class, () -> jobService.retrieveJob("abcdef"));
  }

  @Test
  void testBadIdRetrieveResult() {
    assertThrows(
        JobNotFoundException.class, () -> jobService.retrieveJobResult("abcdef", Object.class));
  }

  @Test
  void setFlightDebugInfoForTest() {
    // Set a FlightDebugInfo so that any job submission should fail on the last step.
    jobService.setFlightDebugInfoForTest(
        FlightDebugInfo.newBuilder().lastStepFailure(true).build());

    String jobId = runFlight("fail for FlightDebugInfo");
    assertThrows(
        InvalidResultStateException.class, () -> jobService.retrieveJobResult(jobId, String.class));
  }

  private void validateJobReport(ApiJobReport jr, int index, List<String> fids) {
    assertThat(jr.getDescription(), equalTo(makeDescription(index)));
    assertThat(jr.getId(), equalTo(fids.get(index)));
    assertThat(jr.getStatus(), equalTo(ApiJobReport.StatusEnum.SUCCEEDED));
    assertThat(jr.getStatusCode(), equalTo(HttpStatus.I_AM_A_TEAPOT.value()));
  }

  // Submit a flight; wait for it to finish; return the flight id
  // Use the jobId defaulting in the JobBuilder
  private String runFlight(String description) {
    // workspace must exist in the Dao for authorization check to pass
    UUID workspaceUuid = UUID.randomUUID();
    Workspace workspace =
        Workspace.builder()
            .workspaceId(workspaceUuid)
            .userFacingId(workspaceUuid.toString())
            .workspaceStage(WorkspaceStage.MC_WORKSPACE)
            .description("Workspace for runFlight: " + description)
            .build();
    workspaceDao.createWorkspace(workspace);
    String jobId =
        jobService
            .newJob()
            .description(description)
            .flightClass(JobServiceTestFlight.class)
            .userRequest(testUser)
            .workspaceId(workspaceUuid.toString())
            .operationType(OperationType.CREATE)
            .submit();
    jobService.waitForJob(jobId);
    return jobId;
  }

  private String makeDescription(int ii) {
    return String.format("flight%d", ii);
  }
}
