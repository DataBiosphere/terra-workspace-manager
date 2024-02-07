package bio.terra.workspace.poc;


import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.verify;

import bio.terra.workspace.common.BaseUnitTest;
import bio.terra.workspace.service.job.JobService;
import bio.terra.workspace.service.workspace.model.OperationType;
import com.fasterxml.jackson.core.type.TypeReference;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

public class FlightGeneratorTest extends BaseUnitTest {
  @Autowired private JobService jobService;
  @MockBean private MySpringBean mySpringBean;

  @Test
  void testMultiStep() {
    var initialValue = 3;
    List<TestResult> result =
        jobService
            .newJob()
            .flightClass(TestFlight.class)
            .addParameter(TestFlight.INITIAL_VALUE, new TestResultImpl(initialValue))
            .operationType(OperationType.CREATE)
            .submitAndWait(new TypeReference<>() {});

    assertThat(result, equalTo(List.of(
        new TestResultImpl(initialValue + 2),
        new TestResultImpl(initialValue + 3))));
  }

  @Test
  void testSpringBeanStep() {
    jobService
        .newJob()
        .flightClass(TestSpringBeanFlight.class)
        .operationType(OperationType.CREATE)
        .submitAndWait();

    verify(mySpringBean).doSomething();
  }

}
