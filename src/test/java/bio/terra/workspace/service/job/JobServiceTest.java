// package bio.terra.workspace.service.job;
//
// import static org.hamcrest.CoreMatchers.equalTo;
// import static org.hamcrest.CoreMatchers.notNullValue;
// import static org.hamcrest.MatcherAssert.assertThat;
// import static org.junit.jupiter.api.Assertions.assertThrows;
// import static org.mockito.ArgumentMatchers.any;
//
// import bio.terra.stairway.exception.StairwayException;
// import bio.terra.workspace.app.Main;
// import bio.terra.workspace.generated.model.JobModel;
// import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
// import bio.terra.workspace.service.iam.SamService;
// import bio.terra.workspace.service.job.exception.JobNotFoundException;
// import java.util.ArrayList;
// import java.util.List;
// import java.util.Optional;
// import java.util.UUID;
// import org.broadinstitute.dsde.workbench.client.sam.model.ResourceAndAccessPolicy;
// import org.junit.jupiter.api.BeforeEach;
// import org.junit.jupiter.api.Tag;
// import org.junit.jupiter.api.Test;
// import org.junit.jupiter.api.extension.ExtendWith;
// import org.mockito.Mockito;
// import org.springframework.beans.factory.annotation.Autowired;
// import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
// import org.springframework.boot.test.context.SpringBootTest;
// import org.springframework.boot.test.mock.mockito.MockBean;
// import org.springframework.http.HttpStatus;
// import org.springframework.test.context.ContextConfiguration;
// import org.springframework.test.context.junit.jupiter.SpringExtension;
//
// @Tag("unit")
// @ExtendWith(SpringExtension.class)
// @ContextConfiguration(classes = Main.class)
// @SpringBootTest
// @AutoConfigureMockMvc
// public class JobServiceTest {
//   private AuthenticatedUserRequest testUser =
//       new AuthenticatedUserRequest()
//           .subjectId("StairwayUnit")
//           .email("stairway@unit.com")
//           .token(Optional.of("not-a-real-token"));
//
//   @Autowired private JobService jobService;
//
//   @MockBean private SamService mockSamService;
//
//   @BeforeEach
//   public void setup() {
//     try {
//       Mockito.doReturn(true).when(mockSamService.isAuthorized(any(), any(), any(), any()));
//     } catch (Exception e) {
//       // How does a mock even throw an exception?
//     }
//   }
//
//   @Test
//   public void retrieveTest() throws Exception {
//     // We perform 7 flights and then retrieve and enumerate them.
//     // The fids list should be in exactly the same order as the database ordered by submit time.
//
//     List<String> jobIds = new ArrayList<>();
//     try {
//       List<ResourceAndAccessPolicy> allowedIds = new ArrayList<>();
//       for (int i = 0; i < 7; i++) {
//         String jobId = runFlight(makeDescription(i));
//         jobIds.add(jobId);
//         allowedIds.add(new ResourceAndAccessPolicy().resourceId(jobId));
//       }
//
//       // Test single retrieval
//       testSingleRetrieval(jobIds);
//
//       // Test result retrieval - the body should be the description string
//       testResultRetrieval(jobIds);
//
//       // Retrieve everything
//       testEnumRange(jobIds, 0, 100, allowedIds);
//
//       // Retrieve the middle 3; offset means skip 2 rows
//       testEnumRange(jobIds, 2, 3, allowedIds);
//
//       // Retrieve from the end; should only get the last one back
//       testEnumCount(1, 6, 3, allowedIds);
//
//       // Retrieve past the end; should get nothing
//       testEnumCount(0, 22, 3, allowedIds);
//     } finally {
//       for (String jobId : jobIds) {
//         jobService.releaseJob(jobId, testUser);
//       }
//     }
//   }
//
//   private void testSingleRetrieval(List<String> fids) {
//     JobModel response = jobService.retrieveJob(fids.get(2), testUser);
//     assertThat(response, notNullValue());
//     validateJobModel(response, 2, fids);
//   }
//
//   private void testResultRetrieval(List<String> fids) {
//     JobService.JobResultWithStatus<String> resultHolder =
//         jobService.retrieveJobResult(fids.get(2), String.class, testUser);
//
//     assertThat(resultHolder.getStatusCode(), equalTo(HttpStatus.I_AM_A_TEAPOT));
//     assertThat(resultHolder.getResult(), equalTo(makeDescription(2)));
//   }
//
//   // Get some range and compare it with the fids
//   private void testEnumRange(
//       List<String> fids, int offset, int limit, List<ResourceAndAccessPolicy> resourceIds) {
//     List<JobModel> jobList = jobService.enumerateJobs(offset, limit, testUser);
//     assertThat(jobList, notNullValue());
//     int index = offset;
//     for (JobModel job : jobList) {
//       validateJobModel(job, index, fids);
//       index++;
//     }
//   }
//
//   // Get some range and make sure we got the number we expected
//   private void testEnumCount(
//       int count, int offset, int length, List<ResourceAndAccessPolicy> resourceIds) {
//     List<JobModel> jobList = jobService.enumerateJobs(offset, length, testUser);
//     assertThat(jobList, notNullValue());
//     assertThat(jobList.size(), equalTo(count));
//   }
//
//   @Test
//   public void testBadIdRetrieveJob() {
//     assertThrows(
//         JobNotFoundException.class,
//         () -> {
//           jobService.retrieveJob("abcdef", testUser);
//         });
//   }
//
//   @Test
//   public void testBadIdRetrieveResult() {
//     assertThrows(
//         JobNotFoundException.class,
//         () -> {
//           jobService.retrieveJobResult("abcdef", Object.class, testUser);
//         });
//   }
//
//   private void validateJobModel(JobModel jm, int index, List<String> fids) {
//     assertThat(jm.getDescription(), equalTo(makeDescription(index)));
//     assertThat(jm.getId(), equalTo(fids.get(index)));
//     assertThat(jm.getStatus(), equalTo(JobModel.StatusEnum.SUCCEEDED));
//     assertThat(jm.getStatusCode(), equalTo(HttpStatus.I_AM_A_TEAPOT.value()));
//   }
//
//   // Submit a flight; wait for it to finish; return the flight id
//   private String runFlight(String description) throws StairwayException {
//     String jobId = UUID.randomUUID().toString();
//     jobService.newJob(description, jobId, JobServiceTestFlight.class, null, testUser).submit();
//     jobService.waitForJob(jobId);
//     return jobId;
//   }
//
//   private String makeDescription(int ii) {
//     return String.format("flight%d", ii);
//   }
// }
