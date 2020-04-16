package bio.terra.workspace.service.job;

import bio.terra.stairway.Flight;
import bio.terra.stairway.FlightFilter;
import bio.terra.stairway.FlightFilterOp;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.FlightState;
import bio.terra.stairway.FlightStatus;
import bio.terra.stairway.Stairway;
import bio.terra.stairway.exception.DatabaseOperationException;
import bio.terra.stairway.exception.FlightNotFoundException;
import bio.terra.stairway.exception.StairwayException;
import bio.terra.workspace.app.configuration.ApplicationConfiguration;
import bio.terra.workspace.app.configuration.StairwayJdbcConfiguration;
import bio.terra.workspace.common.utils.SamUtils;
import bio.terra.workspace.generated.model.JobModel;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.job.exception.InternalStairwayException;
import bio.terra.workspace.service.job.exception.InvalidResultStateException;
import bio.terra.workspace.service.job.exception.JobNotCompleteException;
import bio.terra.workspace.service.job.exception.JobNotFoundException;
import bio.terra.workspace.service.job.exception.JobResponseException;
import bio.terra.workspace.service.job.exception.JobUnauthorizedException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class JobService {

  private static final Logger logger = LoggerFactory.getLogger(JobService.class);
  private final Stairway stairway;
  private final SamService samService;
  private final ApplicationConfiguration appConfig;
  private final StairwayJdbcConfiguration stairwayJdbcConfiguration;

  @Autowired
  public JobService(
      SamService samService,
      ApplicationConfiguration appConfig,
      StairwayJdbcConfiguration stairwayJdbcConfiguration,
      ApplicationContext applicationContext,
      ObjectMapper objectMapper) {
    this.samService = samService;
    this.appConfig = appConfig;
    this.stairwayJdbcConfiguration = stairwayJdbcConfiguration;

    ExecutorService executorService =
        Executors.newFixedThreadPool(appConfig.getMaxStairwayThreads());
    StairwayExceptionSerializer serializer = new StairwayExceptionSerializer(objectMapper);
    stairway =
        new Stairway(
            executorService, applicationContext, serializer); // , UUID.randomUUID().toString());
  }

  public static class JobResultWithStatus<T> {
    private T result;
    private HttpStatus statusCode;

    public T getResult() {
      return result;
    }

    public JobResultWithStatus<T> result(T result) {
      this.result = result;
      return this;
    }

    public HttpStatus getStatusCode() {
      return statusCode;
    }

    public JobResultWithStatus<T> statusCode(HttpStatus httpStatus) {
      this.statusCode = httpStatus;
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
    return new JobBuilder(description, jobId, flightClass, request, userReq, this);
  }

  // submit a new job to stairway
  // protected method intended to be called only from JobBuilder
  protected String submit(
      Class<? extends Flight> flightClass, FlightMap parameterMap, String jobId) {
    try {
      stairway.submit(jobId, flightClass, parameterMap);
    } catch (StairwayException stairwayEx) {
      throw new InternalStairwayException(stairwayEx);
    }
    return jobId;
  }

  // submit a new job to stairway, wait for it to finish, then return the result
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

    return retrieveJobResult(jobId, resultClass, userReq).getResult();
  }

  void waitForJob(String jobId) {
    try {
      stairway.waitForFlight(jobId, 10, appConfig.getStairwayTimeoutSeconds() / 10);
    } catch (StairwayException stairwayEx) {
      throw new InternalStairwayException(stairwayEx);
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
          stairwayJdbcConfiguration.getDataSource(),
          stairwayJdbcConfiguration.isForceClean(),
          stairwayJdbcConfiguration.isMigrateUpgrade());

    } catch (StairwayException stairwayEx) {
      throw new InternalStairwayException("Stairway initialization failed", stairwayEx);
    }
  }

  public void releaseJob(String jobId, AuthenticatedUserRequest userReq) {
    try {
      if (userReq != null) {
        // currently, this check will be true for stewards only
        boolean canDeleteAnyJob =
            samService.isAuthorized(
                userReq.getRequiredToken(),
                SamUtils.SAM_WORKSPACE_MANAGER_RESOURCE,
                appConfig.getResourceId(),
                SamUtils.SAM_WORKSPACE_MANAGER_DELETE_JOBS_ACTION);

        // if the user has access to all jobs, no need to check for this one individually
        // otherwise, check that the user has access to this job before deleting
        if (!canDeleteAnyJob) {
          verifyUserAccess(jobId, userReq); // jobId=flightId
        }
      }
      stairway.deleteFlight(jobId, false);
    } catch (StairwayException stairwayEx) {
      throw new InternalStairwayException(stairwayEx);
    }
  }

  public JobModel mapFlightStateToJobModel(FlightState flightState) {
    FlightMap inputParameters = flightState.getInputParameters();
    String description = inputParameters.get(JobMapKeys.DESCRIPTION.getKeyName(), String.class);
    FlightStatus flightStatus = flightState.getFlightStatus();
    String submittedDate = flightState.getSubmitted().toString();
    JobModel.StatusEnum jobStatus = getJobStatus(flightStatus);

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

    JobModel jobModel =
        new JobModel()
            .id(flightState.getFlightId())
            .description(description)
            .status(jobStatus)
            .statusCode(statusCode.value())
            .submitted(submittedDate)
            .completed(completedDate);

    return jobModel;
  }

  private JobModel.StatusEnum getJobStatus(FlightStatus flightStatus) {
    switch (flightStatus) {
      case ERROR:
      case FATAL:
        return JobModel.StatusEnum.FAILED;
      case RUNNING:
        return JobModel.StatusEnum.RUNNING;
      case SUCCESS:
        return JobModel.StatusEnum.SUCCEEDED;
    }
    return JobModel.StatusEnum.FAILED;
  }

  public List<JobModel> enumerateJobs(int offset, int limit, AuthenticatedUserRequest userReq) {

    List<FlightState> flightStateList;
    try {
      FlightFilter filter = new FlightFilter();
      filter.addFilterInputParameter(
          JobMapKeys.SUBJECT_ID.getKeyName(), FlightFilterOp.EQUAL, userReq.getSubjectId());
      flightStateList = stairway.getFlights(offset, limit, filter);
    } catch (StairwayException stairwayEx) {
      throw new InternalStairwayException(stairwayEx);
    }

    List<JobModel> jobModelList = new ArrayList<>();
    for (FlightState flightState : flightStateList) {
      JobModel jobModel = mapFlightStateToJobModel(flightState);
      jobModelList.add(jobModel);
    }
    return jobModelList;
  }

  public JobModel retrieveJob(String jobId, AuthenticatedUserRequest userReq) {

    try {
      verifyUserAccess(jobId, userReq); // jobId=flightId
      FlightState flightState = stairway.getFlightState(jobId);
      return mapFlightStateToJobModel(flightState);
    } catch (StairwayException stairwayEx) {
      throw new InternalStairwayException(stairwayEx);
    }
  }

  /**
   * There are four cases to handle here:
   *
   * <ol>
   *   <li>Flight is still running. Throw an JobNotComplete exception
   *   <li>Successful flight: extract the resultMap RESPONSE as the target class. If a
   *       statusContainer is present, we try to retrieve the STATUS_CODE from the resultMap and
   *       store it in the container. That allows flight steps used in async REST API endpoints to
   *       set alternate success status codes. The status code defaults to OK, if it is not set in
   *       the resultMap.
   *   <li>Failed flight: if there is an exception, throw it. Note that we can only throw
   *       RuntimeExceptions to be handled by the global exception handler. Non-runtime exceptions
   *       require throw clauses on the controller methods; those are not present in the
   *       swagger-generated code, so it introduces a mismatch. Instead, in this code if the caught
   *       exception is not a runtime exception, then we throw JobResponseException passing in the
   *       Throwable to the exception. In the global exception handler, we retrieve the Throwable
   *       and use the error text from that in the error model
   *   <li>Failed flight: no exception present. We throw InvalidResultState exception
   * </ol>
   *
   * @param jobId to process
   * @return object of the result class pulled from the result map
   */
  public <T> JobResultWithStatus<T> retrieveJobResult(
      String jobId, Class<T> resultClass, AuthenticatedUserRequest userReq) {

    try {
      verifyUserAccess(jobId, userReq); // jobId=flightId
      return retrieveJobResultWorker(jobId, resultClass);
    } catch (StairwayException stairwayEx) {
      throw new InternalStairwayException(stairwayEx);
    }
  }

  private <T> JobResultWithStatus<T> retrieveJobResultWorker(String jobId, Class<T> resultClass)
      throws StairwayException {
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
            throw (RuntimeException) exception;
          } else {
            throw new JobResponseException("wrap non-runtime exception", exception);
          }
        }
        throw new InvalidResultStateException("Failed operation with no exception reported");

      case SUCCESS:
        HttpStatus statusCode =
            resultMap.get(JobMapKeys.STATUS_CODE.getKeyName(), HttpStatus.class);
        if (statusCode == null) {
          statusCode = HttpStatus.OK;
        }
        return new JobResultWithStatus<T>()
            .statusCode(statusCode)
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
    } catch (DatabaseOperationException ex) {
      throw new InternalStairwayException("Stairway exception looking up the job", ex);
    } catch (FlightNotFoundException ex) {
      throw new JobNotFoundException("Job not found", ex);
    }
  }
}
