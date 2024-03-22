package bio.terra.workspace.common.flightGenerator;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import bio.terra.workspace.common.BaseSpringBootUnitTest;
import bio.terra.workspace.service.job.JobService;
import bio.terra.workspace.service.job.exception.InternalStairwayException;
import bio.terra.workspace.service.workspace.model.OperationType;
import com.fasterxml.jackson.core.type.TypeReference;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

public class FlightGeneratorTest extends BaseSpringBootUnitTest {
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

    assertThat(
        result,
        equalTo(
            List.of(new TestResultImpl(initialValue + 2), new TestResultImpl(initialValue + 3))));
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

  @Test
  void testUndo() {
    doThrow(new RuntimeException("test")).when(mySpringBean).throwException();

    assertThrows(
        RuntimeException.class,
        () ->
            jobService
                .newJob()
                .flightClass(TestUndoFlight.class)
                .operationType(OperationType.CREATE)
                .submitAndWait());

    verify(mySpringBean).testUndo();
  }

  @Test
  void testMissingUndo() {
    assertThrows(
        InternalStairwayException.class,
        () ->
            jobService
                .newJob()
                .flightClass(TestMissingUndoFlight.class)
                .operationType(OperationType.CREATE)
                .submitAndWait());
  }

  @Test
  void testUndoNotFound() {
    assertThrows(
        InternalStairwayException.class,
        () ->
            jobService
                .newJob()
                .flightClass(TestUndoNotFoundFlight.class)
                .operationType(OperationType.CREATE)
                .submitAndWait());
  }
}
