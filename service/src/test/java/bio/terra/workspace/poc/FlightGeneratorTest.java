package bio.terra.workspace.poc;


import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

import bio.terra.workspace.common.BaseUnitTest;
import bio.terra.workspace.service.job.JobService;
import bio.terra.workspace.service.workspace.model.OperationType;
import com.fasterxml.jackson.core.type.TypeReference;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class FlightGeneratorTest extends BaseUnitTest {
  @Autowired private JobService jobService;

  @Test
  void emptyStringJobIdTest() {
    var initialValue = 3;
    var jobId = runFlight(initialValue);
    var resultHolder = jobService.retrieveJobResult(jobId, null, new TypeReference<List<TestResult>>() {});
    assertThat(resultHolder.getException(), nullValue());
    assertThat(resultHolder.getResult(), equalTo(List.of(
        new TestResultImpl(initialValue + 2),
        new TestResultImpl(initialValue + 3))));
  }

  private String runFlight(int initialValue) {
    String jobId =
        jobService
            .newJob()
            .flightClass(TestFlight.class)
            .addParameter(TestFlight.INITIAL_VALUE, new TestResultImpl(initialValue))
            .operationType(OperationType.CREATE)
            .submit();
    jobService.waitForJob(jobId);
    return jobId;
  }

}
