package bio.terra.workspace.service.workspace.flight.cloud.gcp;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.DuplicateFlightIdException;
import bio.terra.stairway.exception.FlightNotFoundException;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.common.utils.FlightBeanBag;
import bio.terra.workspace.common.utils.FlightUtils;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import bio.terra.workspace.service.workspace.flight.removeuser.ResourceRolePair;
import com.fasterxml.jackson.core.type.TypeReference;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Stream;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RemoveNativeAccessToPrivateResourcesStep implements Step {
  private static final Logger logger =
      LoggerFactory.getLogger(RemoveNativeAccessToPrivateResourcesStep.class);

  @Override
  public StepResult doStep(FlightContext context) throws InterruptedException, RetryException {
    FlightMap workingMap = context.getWorkingMap();
    List<ResourceRolePair> resourceRolesPairs =
        FlightUtils.getRequired(
            workingMap, ControlledResourceKeys.RESOURCE_ROLES_TO_REMOVE, new TypeReference<>() {});

    Map<UUID, String> flightIds =
        FlightUtils.getRequired(
            context.getWorkingMap(), WorkspaceFlightMapKeys.FLIGHT_IDS, new TypeReference<>() {});

    try {
      var maybeFailure =
          resourceRolesPairs.stream()
              .flatMap(
                  resourceRolePair ->
                      maybeLaunchRemoveNativeAccessFlight(context, resourceRolePair, flightIds))
              .map(flightId -> waitForSubFlight(flightId, context))
              .filter(result -> !result.isSuccess())
              .findAny();

      return maybeFailure.orElseGet(StepResult::getStepResultSuccess);
    } catch (RuntimeException e) {
      // unwrap InterruptedException wrapped in RuntimeException by the lambdas
      if (e.getCause() != null && e.getCause() instanceof InterruptedException) {
        throw (InterruptedException) e.getCause();
      } else {
        throw e;
      }
    }
  }

  /**
   * Wait for a subflight to complete. This method is called in a lambda, which doesn't allow
   * checked exceptions. So we wrap InterruptedException in a RuntimeException and unwrap later.
   *
   * @param flightId the id of the subflight to wait for
   * @param context the flight context
   * @return the result of the subflight
   */
  private static StepResult waitForSubFlight(String flightId, FlightContext context) {
    try {
      return FlightUtils.waitForSubflightCompletion(context.getStairway(), flightId)
          .convertToStepResult();
    } catch (InterruptedException e) {
      // this method is called in a lambda, which doesn't allow checked exceptions. So we wrap
      // InterruptedException in a RuntimeException and unwrap later.
      throw new RuntimeException(e);
    }
  }

  /**
   * @return a stream containing the resource role pair if the subflight to remove native access
   *     completed successfully, empty otherwise
   */
  private static Stream<ResourceRolePair> getSuccessfullyRevoked(
      String flightId, ResourceRolePair resourceRolePair, FlightContext context) {
    try {
      var result =
          FlightUtils.waitForSubflightCompletion(context.getStairway(), flightId)
              .convertToStepResult();
      if (result.isSuccess()) {
        return Stream.of(resourceRolePair);
      } else {
        return Stream.empty();
      }
    } catch (InterruptedException e) {
      // this method is called in a lambda, which doesn't allow checked exceptions. So we wrap
      // InterruptedException in a RuntimeException and unwrap later.
      throw new RuntimeException(e);
    } catch (FlightNotFoundException e) {
      // there was no subflight, so return an empty stream
      return Stream.empty();
    }
  }

  /**
   * Launch a subflight to remove native access to a private resource. This method is called in a
   * lambda, which doesn't allow checked exceptions. So we wrap InterruptedException in a
   * RuntimeException and unwrap later. This will only launch a subflight if the resource has steps
   * to remove native access (getRemoveNativeAccessSteps returns non-empty list).
   *
   * @param context the flight context
   * @param resourceRolePair the resource to remove native access from
   * @param flightIds the map of resource ids to flight ids
   * @return a stream containing the flight id of the subflight if it was launched, empty otherwise
   */
  @NotNull
  private Stream<String> maybeLaunchRemoveNativeAccessFlight(
      FlightContext context, ResourceRolePair resourceRolePair, Map<UUID, String> flightIds) {
    if (resourceRolePair
        .getResource()
        .getRemoveNativeAccessSteps(FlightBeanBag.getFromObject(context.getApplicationContext()))
        .isEmpty()) {
      return Stream.of();
    } else {
      FlightMap inputs = new FlightMap();
      inputs.put(ControlledResourceKeys.RESOURCE, resourceRolePair.getResource());

      String flightId =
          Objects.requireNonNull(flightIds.get(resourceRolePair.getResource().getResourceId()));
      try {
        context
            .getStairway()
            .submit(flightId, RemoveNativeAccessToPrivateResourcesFlight.class, inputs);
        logger.info(
            "Removing native access to private resource {} in flight {}",
            resourceRolePair.getResource().getResourceId(),
            flightId);
      } catch (DuplicateFlightIdException e) {
        // We will see duplicate id on a retry. Quietly continue as if we just launched it.
      } catch (InterruptedException e) {
        // this method is called in a lambda, which doesn't allow checked exceptions. So we wrap
        // InterruptedException in a RuntimeException and unwrap later.
        throw new RuntimeException(e);
      }
      return Stream.of(flightId);
    }
  }

  /**
   * For each successful subflight that removed native access to a private resource, launch another
   * subflight to restore native access.
   */
  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    FlightMap workingMap = context.getWorkingMap();
    List<ResourceRolePair> resourceRolesPairs =
        FlightUtils.getRequired(
            workingMap, ControlledResourceKeys.RESOURCE_ROLES_TO_REMOVE, new TypeReference<>() {});

    Map<UUID, String> flightIds =
        FlightUtils.getRequired(
            context.getWorkingMap(), WorkspaceFlightMapKeys.FLIGHT_IDS, new TypeReference<>() {});

    try {
      var maybeFailure =
          resourceRolesPairs.stream()
              .flatMap(
                  resourceRolePair ->
                      getSuccessfullyRevoked(
                          flightIds.get(resourceRolePair.getResource().getResourceId()),
                          resourceRolePair,
                          context))
              .map(
                  successfulRevoke ->
                      launchRestoreNativeAccessFlight(context, successfulRevoke, flightIds))
              .map(flightId -> waitForSubFlight(flightId, context))
              .filter(result -> !result.isSuccess())
              .findAny();

      return maybeFailure.orElseGet(StepResult::getStepResultSuccess);
    } catch (RuntimeException e) {
      // unwrap InterruptedException wrapped in RuntimeException by the lambdas
      if (e.getCause() != null && e.getCause() instanceof InterruptedException) {
        throw (InterruptedException) e.getCause();
      } else {
        throw e;
      }
    }
  }

  private String launchRestoreNativeAccessFlight(
      FlightContext context, ResourceRolePair resourceRolePair, Map<UUID, String> flightIds) {
    FlightMap inputs = new FlightMap();
    inputs.put(ControlledResourceKeys.RESOURCE, resourceRolePair.getResource());

    String flightId =
        Objects.requireNonNull(flightIds.get(resourceRolePair.getResource().getResourceId()))
            + "-undo";
    try {
      context
          .getStairway()
          .submit(flightId, RestoreNativeAccessToPrivateResourcesFlight.class, inputs);
      logger.info(
          "Restoring native access to private resource {} in flight {}",
          resourceRolePair.getResource().getResourceId(),
          flightId);
    } catch (DuplicateFlightIdException e) {
      // We will see duplicate id on a retry. Quietly continue as if we just launched it.
    } catch (InterruptedException e) {
      // this method is called in a lambda, which doesn't allow checked exceptions. So we wrap
      // InterruptedException in a RuntimeException and unwrap later.
      throw new RuntimeException(e);
    }
    return flightId;
  }
}
