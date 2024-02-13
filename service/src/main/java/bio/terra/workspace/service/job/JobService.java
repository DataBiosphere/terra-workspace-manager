package bio.terra.workspace.service.job;

import bio.terra.common.exception.ServiceUnavailableException;
import bio.terra.common.logging.LoggingUtils;
import bio.terra.common.stairway.StairwayComponent;
import bio.terra.stairway.Flight;
import bio.terra.stairway.FlightDebugInfo;
import bio.terra.stairway.FlightEnumeration;
import bio.terra.stairway.FlightFilter;
import bio.terra.stairway.FlightFilterOp;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.FlightState;
import bio.terra.stairway.Stairway;
import bio.terra.stairway.exception.DatabaseOperationException;
import bio.terra.stairway.exception.DuplicateFlightIdException;
import bio.terra.stairway.exception.FlightNotFoundException;
import bio.terra.stairway.exception.StairwayException;
import bio.terra.workspace.common.utils.FlightBeanBag;
import bio.terra.workspace.common.utils.FlightUtils;
import bio.terra.workspace.common.utils.MdcHook;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.model.SamConstants.SamWorkspaceAction;
import bio.terra.workspace.service.job.exception.DuplicateJobIdException;
import bio.terra.workspace.service.job.exception.InternalStairwayException;
import bio.terra.workspace.service.job.exception.InvalidResultStateException;
import bio.terra.workspace.service.job.exception.JobNotCompleteException;
import bio.terra.workspace.service.job.exception.JobNotFoundException;
import bio.terra.workspace.service.job.exception.JobResponseException;
import bio.terra.workspace.service.job.model.EnumeratedJob;
import bio.terra.workspace.service.job.model.EnumeratedJobs;
import bio.terra.workspace.service.resource.model.StewardshipType;
import bio.terra.workspace.service.resource.model.WsmResource;
import bio.terra.workspace.service.resource.model.WsmResourceFamily;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import bio.terra.workspace.service.workspace.model.JobStateFilter;
import bio.terra.workspace.service.workspace.model.OperationType;
import com.fasterxml.jackson.core.type.TypeReference;
import com.google.common.annotations.VisibleForTesting;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class JobService {
  private static final int METADATA_ROW_WAIT_SECONDS = 1;
  private static final Duration METADATA_ROW_MAX_WAIT_TIME = Duration.ofSeconds(28);

  private final MdcHook mdcHook;
  private final StairwayComponent stairwayComponent;
  private final FlightBeanBag flightBeanBag;
  private final Logger logger = LoggerFactory.getLogger(JobService.class);
  private final OpenTelemetry openTelemetry;
  private FlightDebugInfo flightDebugInfo;

  @Autowired
  public JobService(
      MdcHook mdcHook,
      StairwayComponent stairwayComponent,
      FlightBeanBag flightBeanBag,
      OpenTelemetry openTelemetry) {
    this.mdcHook = mdcHook;
    this.stairwayComponent = stairwayComponent;
    this.flightBeanBag = flightBeanBag;
    this.openTelemetry = openTelemetry;
  }

  // Fully fluent style of JobBuilder
  public JobBuilder newJob() {
    return new JobBuilder(this, stairwayComponent, mdcHook);
  }

  public OpenTelemetry getOpenTelemetry() {
    return openTelemetry;
  }

  // submit a new job to stairway
  // protected method intended to be called only from JobBuilder
  protected String submit(
      Class<? extends Flight> flightClass, FlightMap parameterMap, String jobId) {
    try {
      stairwayComponent
          .get()
          .submitWithDebugInfo(
              jobId, flightClass, parameterMap, /* shouldQueue= */ false, flightDebugInfo);
    } catch (DuplicateFlightIdException ex) {
      // DuplicateFlightIdException is a more specific StairwayException, and so needs to
      // be checked separately. Allowing duplicate FlightIds is useful for ensuring idempotent
      // behavior of flights.
      logger.warn("Received duplicate job ID: {}", jobId);
      throw new DuplicateJobIdException(String.format("Received duplicate jobId %s", jobId), ex);
    } catch (StairwayException | InterruptedException stairwayEx) {
      throw new InternalStairwayException(stairwayEx);
    }
    return jobId;
  }

  // Submit a new job to stairway, wait for it to finish, then return the result.
  // This will throw any exception raised by the flight.
  // protected method intended to be called only from JobBuilder
  protected <T> T submitAndWait(
      Class<? extends Flight> flightClass,
      FlightMap parameterMap,
      Class<T> resultClass,
      TypeReference<T> typeReference,
      String jobId) {
    submit(flightClass, parameterMap, jobId);
    waitForJob(jobId);

    JobResultOrException<T> resultOrException =
        retrieveJobResult(jobId, resultClass, typeReference);
    if (resultOrException.getException() != null) {
      throw resultOrException.getException();
    }
    return resultOrException.getResult();
  }

  public void waitForJob(String jobId) {
    try {
      FlightUtils.waitForJobFlightCompletion(stairwayComponent.get(), jobId);
    } catch (Exception ex) {
      throw new InternalStairwayException(ex);
    }
  }

  /**
   * List Stairway flights related to a workspace. These inputs are translated into inputs to
   * Stairway's getFlights calls. The resulting flights are translated into enumerated jobs. The
   * jobs are ordered by submit time.
   *
   * @param workspaceUuid workspace we are listing in
   * @param limit max number of jobs to return
   * @param pageToken optional starting place in the result set; start at beginning if missing
   * @param cloudResourceType optional filter by cloud resource type
   * @param stewardshipType optional filter by stewardship type
   * @param resourceName optional filter by resource name
   * @param jobStateFilter optional filter by job state
   * @return POJO containing the results
   */
  public EnumeratedJobs enumerateJobs(
      UUID workspaceUuid,
      int limit,
      @Nullable String pageToken,
      @Nullable WsmResourceFamily cloudResourceType,
      @Nullable StewardshipType stewardshipType,
      @Nullable String resourceName,
      @Nullable JobStateFilter jobStateFilter) {
    FlightEnumeration flightEnumeration;
    try {
      FlightFilter filter =
          buildFlightFilter(
              workspaceUuid, cloudResourceType, stewardshipType, resourceName, jobStateFilter);
      flightEnumeration = stairwayComponent.get().getFlights(pageToken, limit, filter);
    } catch (StairwayException | InterruptedException stairwayEx) {
      throw new InternalStairwayException(stairwayEx);
    }

    List<EnumeratedJob> jobList = new ArrayList<>();
    for (FlightState state : flightEnumeration.getFlightStateList()) {
      FlightMap inputParameters = state.getInputParameters();
      OperationType operationType =
          (inputParameters.containsKey(WorkspaceFlightMapKeys.OPERATION_TYPE))
              ? inputParameters.get(WorkspaceFlightMapKeys.OPERATION_TYPE, OperationType.class)
              : OperationType.UNKNOWN;

      WsmResource wsmResource =
          (inputParameters.containsKey(WorkspaceFlightMapKeys.ResourceKeys.RESOURCE))
              ? inputParameters.get(
                  WorkspaceFlightMapKeys.ResourceKeys.RESOURCE, new TypeReference<>() {})
              : null;

      String jobDescription =
          (inputParameters.containsKey(JobMapKeys.DESCRIPTION.getKeyName()))
              ? inputParameters.get(JobMapKeys.DESCRIPTION.getKeyName(), String.class)
              : StringUtils.EMPTY;

      EnumeratedJob enumeratedJob =
          new EnumeratedJob()
              .flightState(state)
              .jobDescription(jobDescription)
              .operationType(operationType)
              .resource(wsmResource);
      jobList.add(enumeratedJob);
    }

    return new EnumeratedJobs()
        .pageToken(flightEnumeration.getNextPageToken())
        .totalResults(flightEnumeration.getTotalFlights())
        .results(jobList);
  }

  private FlightFilter buildFlightFilter(
      UUID workspaceUuid,
      @Nullable WsmResourceFamily cloudResourceType,
      @Nullable StewardshipType stewardshipType,
      @Nullable String resourceName,
      @Nullable JobStateFilter jobStateFilter) {

    FlightFilter filter = new FlightFilter();
    // Always filter by workspace
    filter.addFilterInputParameter(
        WorkspaceFlightMapKeys.WORKSPACE_ID, FlightFilterOp.EQUAL, workspaceUuid.toString());
    // Add optional filters
    Optional.ofNullable(cloudResourceType)
        .ifPresent(
            t ->
                filter.addFilterInputParameter(
                    WorkspaceFlightMapKeys.ResourceKeys.RESOURCE_TYPE, FlightFilterOp.EQUAL, t));
    Optional.ofNullable(stewardshipType)
        .ifPresent(
            t ->
                filter.addFilterInputParameter(
                    WorkspaceFlightMapKeys.ResourceKeys.STEWARDSHIP_TYPE, FlightFilterOp.EQUAL, t));
    Optional.ofNullable(resourceName)
        .ifPresent(
            t ->
                filter.addFilterInputParameter(
                    WorkspaceFlightMapKeys.ResourceKeys.RESOURCE_NAME, FlightFilterOp.EQUAL, t));
    Optional.ofNullable(jobStateFilter).ifPresent(t -> addStateFilter(filter, t));

    return filter;
  }

  private FlightFilter addStateFilter(FlightFilter filter, JobStateFilter jobStateFilter) {
    switch (jobStateFilter) {
      case ALL:
        break;

      case ACTIVE:
        filter.addFilterCompletedTime(FlightFilterOp.EQUAL, null);
        break;

      case COMPLETED:
        filter.addFilterCompletedTime(FlightFilterOp.GREATER_THAN, Instant.EPOCH);
        break;
    }
    return filter;
  }

  @WithSpan
  public FlightState retrieveJob(String jobId) {
    try {
      return stairwayComponent.get().getFlightState(jobId);
    } catch (FlightNotFoundException flightNotFoundException) {
      throw new JobNotFoundException(
          "The flight " + jobId + " was not found", flightNotFoundException);
    } catch (StairwayException | InterruptedException stairwayEx) {
      throw new InternalStairwayException(stairwayEx);
    }
  }

  /** Retrieves Job Result specifying the result class type. */
  @WithSpan
  public <T> JobResultOrException<T> retrieveJobResult(String jobId, Class<T> resultClass) {
    return retrieveJobResult(jobId, resultClass, /* typeReference= */ null);
  }

  /**
   * There are four cases to handle here:
   *
   * <ol>
   *   <li>Flight is still running. Throw an JobNotComplete exception
   *   <li>Successful flight: extract the resultMap RESPONSE as the target class.
   *   <li>Failed flight: if there is an exception, store it in the returned JobResultOrException.
   *       Note that we only store RuntimeExceptions to allow higher-level methods to throw these
   *       exceptions if they choose. Non-runtime exceptions require throw clauses on the controller
   *       methods; those are not present in the swagger-generated code, so it introduces a
   *       mismatch. Instead, in this code if the caught exception is not a runtime exception, then
   *       we store JobResponseException, passing in the Throwable to the exception. In the global
   *       exception handler, we retrieve the Throwable and use the error text from that in the
   *       error model.
   *   <li>Failed flight: no exception present. Throw an InvalidResultState exception
   * </ol>
   *
   * @param jobId to process
   * @param resultClass nullable resultClass. When not null, cast the JobResult to the given class.
   * @param typeReference nullable typeReference. When not null, cast the JobResult to generic type.
   *     When the Job does not have a result (a.k.a. null), both resultClass and typeReference are
   *     set to null.
   * @return object of the result class pulled from the result map
   */
  @WithSpan
  public <T> JobResultOrException<T> retrieveJobResult(
      String jobId, @Nullable Class<T> resultClass, @Nullable TypeReference<T> typeReference) {
    try {
      FlightState flightState = stairwayComponent.get().getFlightState(jobId);
      FlightMap resultMap =
          flightState.getResultMap().orElseThrow(InvalidResultStateException::noResultMap);

      switch (flightState.getFlightStatus()) {
        case FATAL:
          logAlert("WSM Stairway flight {} encountered dismal failure", flightState.getFlightId());
          return handleFailedFlight(flightState);
        case ERROR:
          return handleFailedFlight(flightState);
        case SUCCESS:
          if (resultClass != null) {
            return new JobResultOrException<T>()
                .result(resultMap.get(JobMapKeys.RESPONSE.getKeyName(), resultClass));
          }
          if (typeReference != null) {
            return new JobResultOrException<T>()
                .result(resultMap.get(JobMapKeys.RESPONSE.getKeyName(), typeReference));
          }
          return new JobResultOrException<T>()
              .result(resultMap.get(JobMapKeys.RESPONSE.getKeyName(), (Class<T>) null));
        case RUNNING:
          throw new JobNotCompleteException(
              "Attempt to retrieve job result before job is complete; job id: "
                  + flightState.getFlightId());
        default:
          throw new InvalidResultStateException("Impossible case reached");
      }
    } catch (FlightNotFoundException flightNotFoundException) {
      throw new JobNotFoundException(
          "The flight " + jobId + " was not found", flightNotFoundException);
    } catch (StairwayException | InterruptedException stairwayEx) {
      throw new InternalStairwayException(stairwayEx);
    }
  }

  private <T> JobResultOrException<T> handleFailedFlight(FlightState flightState) {
    Optional<Exception> flightException = flightState.getException();
    if (flightException.isPresent()) {
      Exception exception = flightException.get();
      if (exception instanceof RuntimeException) {
        return new JobResultOrException<T>().exception((RuntimeException) exception);
      } else {
        return new JobResultOrException<T>()
            .exception(new JobResponseException("wrap non-runtime exception", exception));
      }
    }
    logAlert("WSM Stairway flight {} failed with no exception given", flightState.getFlightId());
    throw new InvalidResultStateException("Failed operation with no exception reported.");
  }

  /**
   * Ensure the user in the user request has permission to read the workspace associated with the
   * Job ID. Throws ForbiddenException if not.
   *
   * @param jobId - ID of running job
   * @param userRequest - original user request
   */
  public void verifyUserAccess(String jobId, AuthenticatedUserRequest userRequest) {
    verifyUserAccess(jobId, userRequest, null);
  }

  /**
   * Same assertion as {@link #verifyUserAccess(String, AuthenticatedUserRequest)} but with the
   * additional constraint that the job must be associated with the provided workspace ID.
   */
  public void verifyUserAccess(
      String jobId, AuthenticatedUserRequest userRequest, @Nullable UUID expectedWorkspaceId) {
    try {
      FlightState flightState = stairwayComponent.get().getFlightState(jobId);
      FlightMap inputParameters = flightState.getInputParameters();
      UUID workspaceUuid = inputParameters.get(WorkspaceFlightMapKeys.WORKSPACE_ID, UUID.class);
      if (expectedWorkspaceId != null && !(expectedWorkspaceId.equals(workspaceUuid))) {
        throw new JobNotFoundException(
            String.format("Job %s does not exist in workspace %s.", jobId, workspaceUuid));
      }
      flightBeanBag
          .getWorkspaceService()
          .validateWorkspaceAndAction(userRequest, workspaceUuid, SamWorkspaceAction.READ);
    } catch (DatabaseOperationException | InterruptedException ex) {
      throw new InternalStairwayException("Stairway exception looking up the job", ex);
    } catch (FlightNotFoundException ex) {
      throw new JobNotFoundException("Job not found", ex);
    }
  }

  @VisibleForTesting
  public Stairway getStairway() {
    return stairwayComponent.get();
  }

  /**
   * Sets the {@link FlightDebugInfo} to manipulate future Stairway Flight submissions for testing.
   *
   * <p>This is useful for causing failures on submitted jobs. This should only be used for testing.
   */
  @VisibleForTesting
  public void setFlightDebugInfoForTest(FlightDebugInfo flightDebugInfo) {
    this.flightDebugInfo = flightDebugInfo;
  }

  private void logAlert(String msg, String flightId) {
    // Dismal and unexpected flight failures always require manual intervention,
    // so developers should be notified if they happen. The alert object is deliberately
    // not included in the error message.
    // <p>With the custom json logging configuration we have from TCL
    // (https://github.com/DataBiosphere/terra-common-lib/blob/develop/src/main/java/bio/terra/common/logging/GoogleJsonLayout.java),
    // json-like objects like alertObject in the parameter list will be included in the json
    // blob that we send to stackdriver, even if they aren't actually included in the message.
    // In this case, that means the stackdriver log will have a field terraLogBasedAlert set
    // to true which triggers alerts in the Verily deployment.
    // <p> Json objects will probably still show up in a structured way even if they're also
    // used in the log message so we could have a placeholder for it if we wanted to, but the
    // alertObject is just a map with a single value that we use to flag a message for log-based
    // alerting, so it's not particularly interesting to include in the message.
    logger.error(msg, flightId, LoggingUtils.alertObject());
  }

  public static class JobResultOrException<T> {
    private T result;
    private RuntimeException exception;

    public T getResult() {
      return result;
    }

    public JobResultOrException<T> result(T result) {
      this.result = result;
      return this;
    }

    public RuntimeException getException() {
      return exception;
    }

    public JobResultOrException<T> exception(RuntimeException exception) {
      this.exception = exception;
      return this;
    }
  }

  /**
   * For async workspace or resource creation, we do not want to return to the caller until the
   * metadata row is in the database and, thus, visible to enumeration. This method waits for either
   * the row to show up (the expected success case) or the job to complete (the expected error
   * case). If one of those doesn't happen in the retry window, we throw SERVICE_UNAVAILABLE. The
   * theory is for it not to complete, either WSM is so busy that it cannot schedule the flight or
   * something bad has happened. Either way, SERVICE_UNAVAILABLE seems like a reasonable response.
   *
   * <p>There is no race condition between the two checks. For either termination test, we will make
   * the async return to the client. That path returns the current job state. If the job is
   * complete, the client calls the result endpoint and gets the full result.
   *
   * @param jobId id of the create flight.
   * @param metadataPredicate to decide if the metadata row is there
   */
  public void waitForMetadataOrJob(String jobId, Supplier<Boolean> metadataPredicate) {
    Instant exitTime = Instant.now().plus(METADATA_ROW_MAX_WAIT_TIME);
    try {
      while (Instant.now().isBefore(exitTime)) {
        if (metadataPredicate.get()) {
          return;
        }
        FlightState flightState = getStairway().getFlightState(jobId);
        if (flightState.getCompleted().isPresent()) {
          return;
        }
        TimeUnit.SECONDS.sleep(METADATA_ROW_WAIT_SECONDS);
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      // fall through to throw
    }

    throw new ServiceUnavailableException("Failed to make prompt progress on resource");
  }
}
