package bio.terra.workspace.service.workspace.flight.delete.cloudcontext;

import static bio.terra.stairway.FlightStatus.ERROR;
import static bio.terra.stairway.FlightStatus.FATAL;
import static bio.terra.stairway.FlightStatus.SUCCESS;
import static bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.RESOURCE_DELETE_FLIGHT_PAIR_LIST;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightState;
import bio.terra.stairway.FlightStatus;
import bio.terra.stairway.Stairway;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.FlightNotFoundException;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.common.utils.FlightUtils;
import bio.terra.workspace.common.utils.RetryUtils;
import bio.terra.workspace.db.ResourceDao;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.resource.controlled.ControlledResourceService;
import bio.terra.workspace.service.resource.model.WsmResource;
import bio.terra.workspace.service.workspace.CloudContextService;
import bio.terra.workspace.service.workspace.exceptions.CloudContextDeleteException;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DeleteResourcesStep implements Step {
  private static final Logger logger = LoggerFactory.getLogger(DeleteResourcesStep.class);
  private final CloudContextService cloudContextService;
  private final ControlledResourceService controlledResourceService;
  private final AuthenticatedUserRequest userRequest;
  private final ResourceDao resourceDao;
  private final UUID workspaceUuid;

  public DeleteResourcesStep(
      CloudContextService cloudContextService,
      ControlledResourceService controlledResourceService,
      AuthenticatedUserRequest userRequest,
      ResourceDao resourceDao,
      UUID workspaceUuid) {
    this.cloudContextService = cloudContextService;
    this.controlledResourceService = controlledResourceService;
    this.userRequest = userRequest;
    this.resourceDao = resourceDao;
    this.workspaceUuid = workspaceUuid;
  }

  @Override
  public StepResult doStep(FlightContext context) throws InterruptedException, RetryException {
    // Jackson does not deserialize the list properly with a TypeReference. The array form works.
    ResourceDeleteFlightPair[] resourcePairsArray =
        FlightUtils.getRequired(
            context.getWorkingMap(),
            RESOURCE_DELETE_FLIGHT_PAIR_LIST,
            ResourceDeleteFlightPair[].class);

    List<ResourceDeleteFlightPair> resourcePairs = Arrays.asList(resourcePairsArray);
    logger.info(
        "Flight {} DeleteResourcesStep found {} resources to delete",
        context.getFlightId(),
        resourcePairs.size());

    Stairway stairway = context.getStairway();
    // Loop through the resources, deleting them one at a time
    for (ResourceDeleteFlightPair pair : resourcePairs) {
      try {
        FlightState flightState = stairway.getFlightState(pair.flightId());
        FlightStatus flightStatus = flightState.getFlightStatus();

        // If we already finished this flight, we continue through the list of resources to delete
        if (flightStatus == SUCCESS) {
          continue;
        }

        // If the resource delete flight completed with an error. We complete this delete
        // flight with a different error, reporting the failure
        if (flightStatus == ERROR || flightStatus == FATAL) {
          handleResourceFlightFailure(flightState, pair.resourceId());
        }

        // The flight exists and is still running in Stairway. We wait...
        waitForFlight(pair, stairway);
      } catch (FlightNotFoundException e) {
        launchFlightAndWait(pair, stairway);
      }
    }

    return StepResult.getStepResultSuccess();
  }

  private void handleResourceFlightFailure(FlightState flightState, UUID resourceId) {
    String message;
    Throwable throwable;
    Optional<Exception> flightException = flightState.getException();
    if (flightException.isPresent()) {
      throwable = flightException.get();
      message = throwable.getMessage();
    } else {
      throwable = null;
      message = "flight failed with no error message";
    }

    // Try to get the resource info so we can make a better error message, but don't fail if
    // something goes wrong.
    String resourceName;
    try {
      WsmResource resource = resourceDao.getResource(workspaceUuid, resourceId);
      resourceName = resource.getName();
    } catch (Exception e) {
      logger.warn("Attempt to get resource id {} failed", resourceId, e);
      resourceName = "<not found>";
    }
    throw new CloudContextDeleteException(
        String.format(
            "Cloud context delete failed because resource %s(%s) could not be deleted. Cause was: %s",
            resourceName, resourceId, message),
        throwable);
  }

  private void launchFlightAndWait(ResourceDeleteFlightPair pair, Stairway stairway)
      throws InterruptedException {
    cloudContextService.launchDeleteResourceFlight(
        controlledResourceService, workspaceUuid, pair.resourceId(), pair.flightId(), userRequest);
    waitForFlight(pair, stairway);
  }

  // We are VERY patient with deletion flights. GCP buckets can take many hours to delete if
  // they hold a lot of data.
  private void waitForFlight(ResourceDeleteFlightPair pair, Stairway stairway)
      throws InterruptedException {
    FlightState flightState;
    try {
      flightState =
          RetryUtils.getWithRetry(
              FlightUtils::flightComplete,
              () -> stairway.getFlightState(pair.flightId()),
              Duration.ofHours(7), /* total duration */
              Duration.ofSeconds(15), /* initial sleep */
              1.0, /* factor increase - double each time */
              Duration.ofMinutes(5)); /* max sleep */
    } catch (InterruptedException ie) {
      // Propagate the interrupt
      Thread.currentThread().interrupt();
      throw ie;
    } catch (Exception e) {
      throw new CloudContextDeleteException("Unhandled exception waiting for flight completion", e);
    }
    if (flightState.getFlightStatus() != FlightStatus.SUCCESS) {
      handleResourceFlightFailure(flightState, pair.resourceId());
    }
  }

  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    // We cannot undo the resource delete failures, but we should not trigger a
    // dismal failure. Allow the flight to gracefully unwind and restore the
    // cloud context to a READY state. Some resource may be broken; or maybe
    // there was a state conflict and simply rerunning the delete will succeed.
    return StepResult.getStepResultSuccess();
  }
}
