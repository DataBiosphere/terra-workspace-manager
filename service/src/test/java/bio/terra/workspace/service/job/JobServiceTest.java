package bio.terra.workspace.service.job;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import bio.terra.common.exception.MissingRequiredFieldException;
import bio.terra.stairway.FlightDebugInfo;
import bio.terra.stairway.FlightState;
import bio.terra.workspace.app.controller.shared.JobApiUtils;
import bio.terra.workspace.common.BaseSpringBootUnitTest;
import bio.terra.workspace.common.utils.WorkspaceUnitTestUtils;
import bio.terra.workspace.db.WorkspaceDao;
import bio.terra.workspace.generated.model.ApiJobReport;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.job.exception.InvalidJobIdException;
import bio.terra.workspace.service.job.exception.InvalidResultStateException;
import bio.terra.workspace.service.job.exception.JobNotFoundException;
import bio.terra.workspace.service.job.model.EnumeratedJob;
import bio.terra.workspace.service.job.model.EnumeratedJobs;
import bio.terra.workspace.service.workspace.model.OperationType;
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

class JobServiceTest extends BaseSpringBootUnitTest {
  private final AuthenticatedUserRequest userRequest =
      new AuthenticatedUserRequest()
          .subjectId("StairwayUnit")
          .email("stairway@unit.com")
          .token(Optional.of("not-a-real-token"));

  @Autowired private JobService jobService;
  @Autowired private JobApiUtils jobApiUtils;
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
                .userRequest(userRequest)
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
                .userRequest(userRequest)
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
                .userRequest(userRequest)
                .workspaceId(UUID.randomUUID().toString())
                .submit());
  }

  // Resets the application context before retrieveTest to make sure that the job service does not
  // have some failed jobs left over from other tests.
  @DirtiesContext(methodMode = MethodMode.BEFORE_METHOD)
  @Test
  void retrieveTest() {
    // We perform 7 flights and then retrieve and enumerate them.
    // The fids list should be in exactly the same order as the database ordered by submit time.

    List<String> jobIds1 = new ArrayList<>();
    UUID workspace1 = WorkspaceUnitTestUtils.createWorkspaceWithoutCloudContext(workspaceDao);
    for (int i = 0; i < 3; i++) {
      String jobId = runFlight(workspace1, makeDescription(i));
      jobIds1.add(jobId);
    }

    List<String> jobIds2 = new ArrayList<>();
    UUID workspace2 = WorkspaceUnitTestUtils.createWorkspaceWithoutCloudContext(workspaceDao);

    for (int i = 0; i < 4; i++) {
      String jobId = runFlight(workspace2, makeDescription(i));
      jobIds2.add(jobId);
    }

    // Test single retrieval
    testSingleRetrieval(jobIds1);

    // Test result retrieval - the body should be the description string
    testResultRetrieval(jobIds1);

    // Retrieve each workspace
    testEnumCount(jobIds1, workspace1, 0, 3, 100, null);
    testEnumCount(jobIds2, workspace2, 0, 4, 100, null);

    // Test page token
    String pageToken = testEnumCount(jobIds2, workspace2, 0, 2, 2, null);
    pageToken = testEnumCount(jobIds2, workspace2, 2, 2, 2, pageToken);
    testEnumCount(jobIds2, workspace2, 4, 0, 2, pageToken);
  }

  private void testSingleRetrieval(List<String> fids) {
    FlightState flightState = jobService.retrieveJob(fids.get(2));
    ApiJobReport response = jobApiUtils.mapFlightStateToApiJobReport(flightState);
    assertThat(response, notNullValue());
    validateJobReport(response, 2, fids);
  }

  private void testResultRetrieval(List<String> fids) {
    JobService.JobResultOrException<String> resultHolder =
        jobService.retrieveJobResult(fids.get(2), String.class);

    assertNull(resultHolder.getException());
    assertEquals(resultHolder.getResult(), makeDescription(2));
  }

  // Enumerate and make sure we got the number we expected
  // Validate the result is what we expect
  private String testEnumCount(
      List<String> fids,
      UUID workspaceId,
      int expectedOffset,
      int expectedCount,
      int limit,
      String pageToken) {
    EnumeratedJobs jobList =
        jobService.enumerateJobs(workspaceId, limit, pageToken, null, null, null, null);
    assertNotNull(jobList);
    assertEquals(expectedCount, jobList.getResults().size());
    int index = expectedOffset;
    for (EnumeratedJob job : jobList.getResults()) {
      ApiJobReport jobReport = jobApiUtils.mapFlightStateToApiJobReport(job.getFlightState());
      validateJobReport(jobReport, index, fids);
      index++;
    }

    return jobList.getPageToken();
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

    String jobId = runFlight(UUID.randomUUID(), "fail for FlightDebugInfo");
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
  private String runFlight(UUID workspaceUuid, String description) {
    String jobId =
        jobService
            .newJob()
            .description(description)
            .flightClass(JobServiceTestFlight.class)
            .userRequest(userRequest)
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
