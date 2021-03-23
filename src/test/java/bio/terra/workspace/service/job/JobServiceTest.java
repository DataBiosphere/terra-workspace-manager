package bio.terra.workspace.service.job;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;

import bio.terra.workspace.common.BaseUnitTest;
import bio.terra.workspace.generated.model.ApiJobReport;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.job.exception.InvalidJobIdException;
import bio.terra.workspace.service.job.exception.JobNotFoundException;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;

class JobServiceTest extends BaseUnitTest {
  private final AuthenticatedUserRequest testUser =
      new AuthenticatedUserRequest()
          .subjectId("StairwayUnit")
          .email("stairway@unit.com")
          .token(Optional.of("not-a-real-token"));

  @Autowired private JobService jobService;

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

  @Test
  void emptyStringJobIdTest() {
    String testJobId = "";
    assertThrows(
        InvalidJobIdException.class,
        () ->
            jobService.newJob(
                "description", testJobId, JobServiceTestFlight.class, null, testUser));
  }

  @Test
  void whitespaceStringJobIdTest() {
    String testJobId = "\t ";
    assertThrows(
        InvalidJobIdException.class,
        () ->
            jobService.newJob(
                "description", testJobId, JobServiceTestFlight.class, null, testUser));
  }

  @Test
  void retrieveTest() throws Exception {
    // We perform 7 flights and then retrieve and enumerate them.
    // The fids list should be in exactly the same order as the database ordered by submit time.

    List<String> jobIds = new ArrayList<>();
    try {
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
    } finally {
      for (String jobId : jobIds) {
        jobService.releaseJob(jobId, testUser);
      }
    }
  }

  private void testSingleRetrieval(List<String> fids) {
    ApiJobReport response = jobService.retrieveJob(fids.get(2), testUser);
    assertThat(response, notNullValue());
    validateJobReport(response, 2, fids);
  }

  private void testResultRetrieval(List<String> fids) {
    JobService.JobResultOrException<String> resultHolder =
        jobService.retrieveJobResult(fids.get(2), String.class, testUser);

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
    assertThrows(JobNotFoundException.class, () -> jobService.retrieveJob("abcdef", testUser));
  }

  @Test
  void testBadIdRetrieveResult() {
    assertThrows(
        JobNotFoundException.class,
        () -> jobService.retrieveJobResult("abcdef", Object.class, testUser));
  }

  private void validateJobReport(ApiJobReport jr, int index, List<String> fids) {
    assertThat(jr.getDescription(), equalTo(makeDescription(index)));
    assertThat(jr.getId(), equalTo(fids.get(index)));
    assertThat(jr.getStatus(), equalTo(ApiJobReport.StatusEnum.SUCCEEDED));
    assertThat(jr.getStatusCode(), equalTo(HttpStatus.I_AM_A_TEAPOT.value()));
  }

  // Submit a flight; wait for it to finish; return the flight id
  private String runFlight(String description) {
    String jobId = UUID.randomUUID().toString();
    jobService.newJob(description, jobId, JobServiceTestFlight.class, null, testUser).submit();
    jobService.waitForJob(jobId);
    return jobId;
  }

  private String makeDescription(int ii) {
    return String.format("flight%d", ii);
  }
}
