package bio.terra.workspace.common;

import bio.terra.stairway.Flight;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightDebugInfo;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.FlightState;
import bio.terra.stairway.Stairway;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.stairway.exception.DatabaseOperationException;
import bio.terra.stairway.exception.DuplicateFlightIdException;
import bio.terra.stairway.exception.StairwayExecutionException;
import bio.terra.workspace.generated.model.ApiJobReport;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.job.JobService;
import bio.terra.workspace.service.job.model.EnumeratedJob;
import bio.terra.workspace.service.job.model.EnumeratedJobs;
import bio.terra.workspace.service.resource.model.WsmResource;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Test utilities for working with Stairway. */
public class StairwayTestUtils {
  private static final Logger logger = LoggerFactory.getLogger(StairwayTestUtils.class);

  private StairwayTestUtils() {}

  /**
   * Submits the flight and block until Stairway completes it by polling regularly until the timeout
   * is reached.
   */
  public static FlightState blockUntilFlightCompletes(
      Stairway stairway,
      Class<? extends Flight> flightClass,
      FlightMap inputParameters,
      Duration timeout,
      FlightDebugInfo debugInfo)
      throws DatabaseOperationException,
          StairwayExecutionException,
          InterruptedException,
          DuplicateFlightIdException {
    String flightId = stairway.createFlightId();
    // TODO(PF-1408): Remove/adjust this when all fixes are in
    // ^^^^^^^^^^^^^^^
    // Allow for GCP propagation to complete. In the second part of the PF-1408 work, we can decide
    // the appropriate timeout for those cases and restore timeout control to the tests.
    logger.warn("--> Overriding poll timeout for GCP permission propagation diagnosis <--");
    timeout = Duration.ofMinutes(75);
    // ^^^^^^^^^^^^^^^

    stairway.submitWithDebugInfo(
        flightId, flightClass, inputParameters, /* shouldQueue= */ false, debugInfo);
    return pollUntilComplete(flightId, stairway, Duration.ofSeconds(1), timeout);
  }

  /**
   * Polls stairway until the flight for {@code flightId} completes, or this has polled {@code
   * numPolls} times every {@code pollInterval}.
   */
  public static FlightState pollUntilComplete(
      String flightId, Stairway stairway, Duration pollInterval, Duration timeout)
      throws InterruptedException, DatabaseOperationException {
    for (Instant deadline = Instant.now().plus(timeout);
        Instant.now().isBefore(deadline);
        Thread.sleep(pollInterval.toMillis())) {
      FlightState flightState = stairway.getFlightState(flightId);
      if (!flightState.isActive()) {
        return flightState;
      }
    }
    throw new InterruptedException(
        String.format("Flight [%s] did not complete in the allowed wait time.", flightId));
  }

  public static void enumerateJobsDump(
      JobService jobService, UUID workspaceUuid, AuthenticatedUserRequest userRequest) {
    EnumeratedJobs jobs =
        jobService.enumerateJobs(workspaceUuid, 1000, null, null, null, null, null);

    System.out.printf(
        "Enumerated Jobs: total=%d, pageToken=%s%n", jobs.getTotalResults(), jobs.getPageToken());

    for (EnumeratedJob job : jobs.getResults()) {
      FlightState flightState = job.getFlightState();
      System.out.printf("  Job %s %s%n", flightState.getFlightId(), flightState.getFlightStatus());
      System.out.printf("    description: %s%n", job.getJobDescription());
      System.out.printf("    submitted  : %s%n", flightState.getSubmitted());
      System.out.printf(
          "    completed  : %s%n",
          flightState.getCompleted().map(Instant::toString).orElse("<incomplete>"));
      if (flightState.getException().isPresent()) {
        System.out.printf("   error       : %s%n", flightState.getException().get().getMessage());
      }
      System.out.printf("    operation : %s%n", job.getOperationType());
      if (job.getResource().isPresent()) {
        WsmResource resource = job.getResource().get();
        System.out.println("    resource:");
        System.out.printf("      name: %s%n", resource.getName());
        System.out.printf("      id  : %s%n", resource.getResourceId());
        System.out.printf("      desc: %s%n", resource.getDescription());
        System.out.printf("      stew: %s%n", resource.getStewardshipType());
        System.out.printf("      type: %s%n", resource.getResourceType());
      }
    }
  }

  /**
   * A {@link Step} that always fatally errors on {@link Step#doStep(FlightContext)}. Undo is ok.
   */
  public static class ErrorDoStep implements Step {
    @Override
    public StepResult doStep(FlightContext flightContext) {
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_FATAL);
    }

    @Override
    public StepResult undoStep(FlightContext flightContext) {
      return StepResult.getStepResultSuccess();
    }
  }

  public static boolean jobIsRunning(ApiJobReport jobReport) {
    return jobReport.getStatus().equals(ApiJobReport.StatusEnum.RUNNING);
  }
}
