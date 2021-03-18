package bio.terra.workspace.service.job;

import bio.terra.common.stairway.TracingHook;
import bio.terra.stairway.Flight;
import bio.terra.stairway.FlightFilter;
import bio.terra.stairway.FlightFilterOp;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.FlightState;
import bio.terra.stairway.FlightStatus;
import bio.terra.stairway.Stairway;
import bio.terra.stairway.exception.DatabaseOperationException;
import bio.terra.stairway.exception.DuplicateFlightIdSubmittedException;
import bio.terra.stairway.exception.FlightNotFoundException;
import bio.terra.stairway.exception.StairwayException;
import bio.terra.stairway.exception.StairwayExecutionException;
import bio.terra.workspace.app.configuration.external.IngressConfiguration;
import bio.terra.workspace.app.configuration.external.JobConfiguration;
import bio.terra.workspace.app.configuration.external.StairwayDatabaseConfiguration;
import bio.terra.workspace.common.exception.stairway.StairwayInitializationException;
import bio.terra.workspace.common.utils.ErrorReportUtils;
import bio.terra.workspace.common.utils.FlightBeanBag;
import bio.terra.workspace.common.utils.MdcHook;
import bio.terra.workspace.generated.model.ApiErrorReport;
import bio.terra.workspace.generated.model.ApiJobReport;
import bio.terra.workspace.generated.model.ApiJobReport.StatusEnum;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.job.exception.DuplicateJobIdException;
import bio.terra.workspace.service.job.exception.InternalStairwayException;
import bio.terra.workspace.service.job.exception.InvalidJobIdException;
import bio.terra.workspace.service.job.exception.InvalidResultStateException;
import bio.terra.workspace.service.job.exception.JobNotCompleteException;
import bio.terra.workspace.service.job.exception.JobNotFoundException;
import bio.terra.workspace.service.job.exception.JobResponseException;
import bio.terra.workspace.service.job.exception.JobUnauthorizedException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.annotations.VisibleForTesting;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.opencensus.contrib.spring.aop.Traced;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class JobService {

  private final Stairway stairway;
  private final JobConfiguration jobConfig;
  private final IngressConfiguration ingressConfig;
  private final StairwayDatabaseConfiguration stairwayDatabaseConfiguration;
  private final ScheduledExecutorService executor;
  private final MdcHook mdcHook;

  private final Logger logger = LoggerFactory.getLogger(JobService.class);

  @Autowired
  public JobService(
      JobConfiguration jobConfig,
      IngressConfiguration ingressConfig,
      StairwayDatabaseConfiguration stairwayDatabaseConfiguration,
      FlightBeanBag applicationContext,
      MdcHook mdcHook,
      ObjectMapper objectMapper) {
    this.jobConfig = jobConfig;
    this.ingressConfig = ingressConfig;
    this.stairwayDatabaseConfiguration = stairwayDatabaseConfiguration;
    this.executor = Executors.newScheduledThreadPool(jobConfig.getMaxThreads());
    this.mdcHook = mdcHook;
    StairwayExceptionSerializer serializer = new StairwayExceptionSerializer(objectMapper);
    Stairway.Builder builder =
        Stairway.newBuilder()
            .applicationContext(applicationContext)
            .exceptionSerializer(serializer)
            .keepFlightLog(true)
            .enableWorkQueue(false)
            .stairwayHook(mdcHook)
            .stairwayHook(new TracingHook());
    try {
      stairway = new Stairway(builder);
    } catch (StairwayExecutionException e) {
      throw new StairwayInitializationException("Error starting stairway: ", e);
    }
  }

  @SuppressFBWarnings(value = "NM_CLASS_NOT_EXCEPTION", justification = "Non-exception by design.")
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

  // The result of an asynchronous job is a ApiJobReport and exactly one of a job result
  // or an ApiErrorReport. If the job is incomplete, only jobReport will be present.
  public static class AsyncJobResult<T> {
    private ApiJobReport jobReport;
    private T result;
    private ApiErrorReport errorReport;

    public T getResult() {
      return result;
    }

    public AsyncJobResult<T> result(T result) {
      this.result = result;
      return this;
    }

    public ApiErrorReport getApiErrorReport() {
      return errorReport;
    }

    public AsyncJobResult<T> errorReport(ApiErrorReport errorReport) {
      this.errorReport = errorReport;
      return this;
    }

    public ApiJobReport getJobReport() {
      return jobReport;
    }

    public AsyncJobResult<T> jobReport(ApiJobReport jobReport) {
      this.jobReport = jobReport;
      return this;
    }
  }

  // creates a new JobBuilder object and returns it.
  public JobBuilder newJob(
      String description,
      String jobId,
      Class<? extends Flight> flightClass,
      Object request,
      AuthenticatedUserRequest userReq) {

    // If clients provide a non-null job ID, it cannot be whitespace-only
    if (StringUtils.isWhitespace(jobId)) {
      throw new InvalidJobIdException("jobId cannot be whitespace-only.");
    }

    return new JobBuilder(description, jobId, flightClass, request, userReq, this)
        .addParameter(MdcHook.MDC_FLIGHT_MAP_KEY, mdcHook.getSerializedCurrentContext())
        .addParameter(
            TracingHook.SUBMISSION_SPAN_CONTEXT_MAP_KEY,
            TracingHook.serializeCurrentTracingContext());
  }

  // submit a new job to stairway
  // protected method intended to be called only from JobBuilder
  protected String submit(
      Class<? extends Flight> flightClass, FlightMap parameterMap, String jobId) {
    try {
      stairway.submit(jobId, flightClass, parameterMap);
    } catch (DuplicateFlightIdSubmittedException ex) {
      // DuplicateFlightIdSubmittedException is a more specific StairwayException, and so needs to
      // be checked separately. Allowing duplicate FlightIds is useful for ensuring idempotent
      // behavior of flights.
      logger.warn("Received duplicate job ID: {}", jobId);
      throw new DuplicateJobIdException("Received duplicate jobId, see logs for details", ex);
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
      String jobId) {
    submit(flightClass, parameterMap, jobId);
    waitForJob(jobId);
    AuthenticatedUserRequest userReq =
        parameterMap.get(JobMapKeys.AUTH_USER_INFO.getKeyName(), AuthenticatedUserRequest.class);

    JobResultOrException<T> resultOrException = retrieveJobResult(jobId, resultClass, userReq);
    if (resultOrException.getException() != null) {
      throw resultOrException.getException();
    }
    return resultOrException.getResult();
  }

  @VisibleForTesting
  public void waitForJob(String jobId) {
    try {
      int pollSeconds = jobConfig.getPollingIntervalSeconds();
      int pollCycles = jobConfig.getTimeoutSeconds() / jobConfig.getPollingIntervalSeconds();
      for (int i = 0; i < pollCycles; i++) {
        ScheduledFuture<FlightState> futureState =
            executor.schedule(new PollFlightTask(stairway, jobId), pollSeconds, TimeUnit.SECONDS);
        FlightState state = futureState.get();
        if (state != null) {
          // Indicates job has completed, though not necessarily successfully.
          return;
        } else {
          // Indicates job has not completed yet, continue polling
          continue;
        }
      }
    } catch (InterruptedException | ExecutionException stairwayEx) {
      throw new InternalStairwayException(stairwayEx);
    }
    // Indicates we timed out waiting for completion, throw exception
    throw new InternalStairwayException("Flight did not complete in the allowed wait time");
  }

  private static class PollFlightTask implements Callable<FlightState> {
    private final Stairway stairway;
    private final String flightId;

    public PollFlightTask(Stairway stairway, String flightId) {
      this.stairway = stairway;
      this.flightId = flightId;
    }

    @Override
    public FlightState call() throws Exception {
      FlightState state = stairway.getFlightState(flightId);
      if (!state.isActive()) {
        return state;
      } else {
        return null;
      }
    }
  }

  /**
   * This method is called from StartupInitializer as part of the sequence of migrating databases
   * and recovering any jobs; i.e., Stairway flights. It is moved here so that JobService
   * encapsulates all of the Stairway interaction.
   */
  public void initialize() {
    try {
      stairway.initialize(
          stairwayDatabaseConfiguration.getDataSource(),
          stairwayDatabaseConfiguration.isForceClean(),
          stairwayDatabaseConfiguration.isMigrateUpgrade());
      stairway.recoverAndStart(null);

    } catch (StairwayException | InterruptedException stairwayEx) {
      throw new InternalStairwayException("Stairway initialization failed", stairwayEx);
    }
  }

  @Traced
  public void releaseJob(String jobId, AuthenticatedUserRequest userReq) {
    try {
      verifyUserAccess(jobId, userReq); // jobId=flightId
      stairway.deleteFlight(jobId, false);
    } catch (StairwayException | InterruptedException stairwayEx) {
      throw new InternalStairwayException(stairwayEx);
    }
  }

  public ApiJobReport mapFlightStateToApiJobReport(FlightState flightState) {
    FlightMap inputParameters = flightState.getInputParameters();
    String description = inputParameters.get(JobMapKeys.DESCRIPTION.getKeyName(), String.class);
    FlightStatus flightStatus = flightState.getFlightStatus();
    String submittedDate = flightState.getSubmitted().toString();
    ApiJobReport.StatusEnum jobStatus = getJobStatus(flightStatus);

    String completedDate = null;
    HttpStatus statusCode = HttpStatus.ACCEPTED;

    if (flightState.getCompleted().isPresent()) {
      FlightMap resultMap = getResultMap(flightState);
      // The STATUS_CODE return only needs to be used to return alternate success responses.
      // If it is not present, then we set it to the default OK status.
      statusCode = resultMap.get(JobMapKeys.STATUS_CODE.getKeyName(), HttpStatus.class);
      if (statusCode == null) {
        statusCode = HttpStatus.OK;
      }

      completedDate = flightState.getCompleted().get().toString();
    }

    ApiJobReport jobReport =
        new ApiJobReport()
            .id(flightState.getFlightId())
            .description(description)
            .status(jobStatus)
            .statusCode(statusCode.value())
            .submitted(submittedDate)
            .completed(completedDate)
            .resultURL(resultUrlFromFlightState(flightState));

    return jobReport;
  }

  private String resultUrlFromFlightState(FlightState flightState) {
    String resultPath =
        flightState.getInputParameters().get(JobMapKeys.RESULT_PATH.getKeyName(), String.class);
    if (resultPath == null) {
      resultPath = "";
    }
    return Path.of(ingressConfig.getDomainName(), resultPath).toString();
  }

  private ApiJobReport.StatusEnum getJobStatus(FlightStatus flightStatus) {
    switch (flightStatus) {
      case RUNNING:
        return ApiJobReport.StatusEnum.RUNNING;
      case SUCCESS:
        return ApiJobReport.StatusEnum.SUCCEEDED;
      case ERROR:
      case FATAL:
      default:
        return ApiJobReport.StatusEnum.FAILED;
    }
  }

  public List<ApiJobReport> enumerateJobs(int offset, int limit, AuthenticatedUserRequest userReq) {

    List<FlightState> flightStateList;
    try {
      FlightFilter filter = new FlightFilter();
      filter.addFilterInputParameter(
          JobMapKeys.SUBJECT_ID.getKeyName(), FlightFilterOp.EQUAL, userReq.getSubjectId());
      flightStateList = stairway.getFlights(offset, limit, filter);
    } catch (StairwayException | InterruptedException stairwayEx) {
      throw new InternalStairwayException(stairwayEx);
    }

    List<ApiJobReport> jobReportList = new ArrayList<>();
    for (FlightState flightState : flightStateList) {
      ApiJobReport jobReport = mapFlightStateToApiJobReport(flightState);
      jobReportList.add(jobReport);
    }
    return jobReportList;
  }

  @Traced
  public ApiJobReport retrieveJob(String jobId, AuthenticatedUserRequest userReq) {

    try {
      verifyUserAccess(jobId, userReq); // jobId=flightId
      FlightState flightState = stairway.getFlightState(jobId);
      return mapFlightStateToApiJobReport(flightState);
    } catch (StairwayException | InterruptedException stairwayEx) {
      throw new InternalStairwayException(stairwayEx);
    }
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
   * @return object of the result class pulled from the result map
   */
  @Traced
  public <T> JobResultOrException<T> retrieveJobResult(
      String jobId, Class<T> resultClass, AuthenticatedUserRequest userReq) {

    try {
      verifyUserAccess(jobId, userReq); // jobId=flightId
      return retrieveJobResultWorker(jobId, resultClass);
    } catch (StairwayException | InterruptedException stairwayEx) {
      throw new InternalStairwayException(stairwayEx);
    }
  }

  /**
   * Retrieves the result of an asynchronous job.
   *
   * <p>Stairway has no concept of synchronous vs asynchronous flights. However, MC Terra has a
   * service-level standard result for asynchronous jobs which includes a ApiJobReport and either a
   * result or error if the job is complete. This is a convenience for callers who would otherwise
   * need to construct their own AsyncJobResult object.
   *
   * <p>Unlike retrieveJobResult, this will not throw for a flight in progress. Instead, it will
   * return a ApiJobReport without a result or error.
   */
  public <T> AsyncJobResult<T> retrieveAsyncJobResult(
      String jobId, Class<T> resultClass, AuthenticatedUserRequest userReq) {
    try {
      verifyUserAccess(jobId, userReq); // jobId=flightId
      ApiJobReport jobReport = retrieveJob(jobId, userReq);
      if (jobReport.getStatus().equals(StatusEnum.RUNNING)) {
        return new AsyncJobResult<T>().jobReport(jobReport);
      }

      JobResultOrException<T> resultOrException = retrieveJobResultWorker(jobId, resultClass);
      final ApiErrorReport errorReport;
      if (jobReport.getStatus().equals(StatusEnum.FAILED)) {
        errorReport = ErrorReportUtils.buildApiErrorReport(resultOrException.getException());
      } else {
        errorReport = null;
      }
      return new AsyncJobResult<T>()
          .jobReport(jobReport)
          .result(resultOrException.getResult())
          .errorReport(errorReport);
    } catch (StairwayException | InterruptedException stairwayEx) {
      throw new InternalStairwayException(stairwayEx);
    }
  }

  private <T> JobResultOrException<T> retrieveJobResultWorker(String jobId, Class<T> resultClass)
      throws StairwayException, InterruptedException {
    FlightState flightState = stairway.getFlightState(jobId);
    FlightMap resultMap = flightState.getResultMap().orElse(null);
    if (resultMap == null) {
      throw new InvalidResultStateException("No result map returned from flight");
    }

    switch (flightState.getFlightStatus()) {
      case FATAL:
      case ERROR:
        if (flightState.getException().isPresent()) {
          Exception exception = flightState.getException().get();
          if (exception instanceof RuntimeException) {
            return new JobResultOrException<T>().exception((RuntimeException) exception);
          } else {
            return new JobResultOrException<T>()
                .exception(new JobResponseException("wrap non-runtime exception", exception));
          }
        }
        throw new InvalidResultStateException("Failed operation with no exception reported");

      case SUCCESS:
        return new JobResultOrException<T>()
            .result(resultMap.get(JobMapKeys.RESPONSE.getKeyName(), resultClass));

      case RUNNING:
        throw new JobNotCompleteException(
            "Attempt to retrieve job result before job is complete; job id: "
                + flightState.getFlightId());

      default:
        throw new InvalidResultStateException("Impossible case reached");
    }
  }

  private FlightMap getResultMap(FlightState flightState) {
    FlightMap resultMap = flightState.getResultMap().orElse(null);
    if (resultMap == null) {
      throw new InvalidResultStateException("No result map returned from flight");
    }
    return resultMap;
  }

  private void verifyUserAccess(String jobId, AuthenticatedUserRequest userReq) {
    try {
      FlightState flightState = stairway.getFlightState(jobId);
      FlightMap inputParameters = flightState.getInputParameters();
      String flightSubjectId =
          inputParameters.get(JobMapKeys.SUBJECT_ID.getKeyName(), String.class);
      if (!StringUtils.equals(flightSubjectId, userReq.getSubjectId())) {
        throw new JobUnauthorizedException("Unauthorized");
      }
    } catch (DatabaseOperationException | InterruptedException ex) {
      throw new InternalStairwayException("Stairway exception looking up the job", ex);
    } catch (FlightNotFoundException ex) {
      throw new JobNotFoundException("Job not found", ex);
    }
  }

  @VisibleForTesting
  public Stairway getStairway() {
    return stairway;
  }
}
