package bio.terra.workspace.common.flightGenerator;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import bio.terra.stairway.RetryRuleExponentialBackoff;
import bio.terra.stairway.RetryRuleFixedInterval;
import bio.terra.stairway.RetryRuleNone;
import bio.terra.stairway.RetryRuleRandomBackoff;
import bio.terra.workspace.common.BaseSpringBootUnitTest;
import bio.terra.workspace.service.job.JobService;
import bio.terra.workspace.service.workspace.model.OperationType;
import com.fasterxml.jackson.core.type.TypeReference;
import java.lang.reflect.Proxy;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

public class FlightGeneratorTest extends BaseSpringBootUnitTest {
  @Autowired private JobService jobService;
  @MockBean private MySpringBean mySpringBean;

  @Mock private FlightGenerator mockFlightGenerator;

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
        InvalidUndoAnnotationException.class, setupStepProxyWithMockFlightGenerator()::missingUndo);
  }

  @Test
  void testUndoNotFound() {
    assertThrows(
        InvalidUndoAnnotationException.class,
        setupStepProxyWithMockFlightGenerator()::undoDoesNotExist);
  }

  @Test
  void testFixedIntervalRetry() {
    setupStepProxyWithMockFlightGenerator().fixedRetryMethod();
    verify(mockFlightGenerator).addStep(any(), isA(RetryRuleFixedInterval.class));
  }

  @Test
  void testExponentialRetry() {
    setupStepProxyWithMockFlightGenerator().exponentialRetryMethod();
    verify(mockFlightGenerator).addStep(any(), isA(RetryRuleExponentialBackoff.class));
  }

  @Test
  void testRandomRetry() {
    setupStepProxyWithMockFlightGenerator().randomRetryMethod();
    verify(mockFlightGenerator).addStep(any(), isA(RetryRuleRandomBackoff.class));
  }

  @Test
  void testNoRetry() {
    setupStepProxyWithMockFlightGenerator().noRetryMethod();
    verify(mockFlightGenerator).addStep(any(), isA(RetryRuleNone.class));
  }

  @Test
  void testCustomRetryAnnotation() {
    setupStepProxyWithMockFlightGenerator().customRetryMethod();
    verify(mockFlightGenerator).addStep(any(), isA(RetryRuleFixedInterval.class));
  }

  @Test
  void testTooManyRetryAnnotations() {
    assertThrows(
        InvalidRetryAnnotationException.class,
        setupStepProxyWithMockFlightGenerator()::tooManyRetryMethod);
  }

  @Test
  void testMissingRetryAnnotation() {
    assertThrows(
        InvalidRetryAnnotationException.class,
        setupStepProxyWithMockFlightGenerator()::missingRetryMethod);
  }

  private TestStep setupStepProxyWithMockFlightGenerator() {
    var invocationHandler = new StepInvocationHandler(mockFlightGenerator, new TestStepImpl(), 0);
    return (TestStep)
        Proxy.newProxyInstance(
            getClass().getClassLoader(), new Class[] {TestStep.class}, invocationHandler);
  }
}
