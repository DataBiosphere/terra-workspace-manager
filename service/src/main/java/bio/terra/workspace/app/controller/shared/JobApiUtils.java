package bio.terra.workspace.app.controller.shared;

import bio.terra.stairway.FlightMap;
import bio.terra.stairway.FlightState;
import bio.terra.stairway.FlightStatus;
import bio.terra.stairway.exception.StairwayException;
import bio.terra.workspace.app.configuration.external.IngressConfiguration;
import bio.terra.workspace.common.utils.ErrorReportUtils;
import bio.terra.workspace.generated.model.ApiErrorReport;
import bio.terra.workspace.generated.model.ApiJobReport;
import bio.terra.workspace.generated.model.ApiJobResult;
import bio.terra.workspace.service.job.JobMapKeys;
import bio.terra.workspace.service.job.JobService;
import bio.terra.workspace.service.job.exception.InternalStairwayException;
import bio.terra.workspace.service.job.exception.InvalidResultStateException;
import com.fasterxml.jackson.core.type.TypeReference;
import java.nio.file.Path;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * This class has common methods used by controllers to convert JobService results into API format.
 */
@Component
public class JobApiUtils {
  private final JobService jobService;
  private final IngressConfiguration ingressConfig;

  @Autowired
  JobApiUtils(JobService jobService, IngressConfiguration ingressConfig) {
    this.jobService = jobService;
    this.ingressConfig = ingressConfig;
  }

  /** Retrieves the result of an asynchronous job. */
  public <T> AsyncJobResult<T> retrieveAsyncJobResult(String jobId, Class<T> resultClass) {
    return retrieveAsyncJobResult(jobId, resultClass, /* typeReference= */ null);
  }

  /** Retrieves the result of an asynchronous job. */
  public <T> AsyncJobResult<T> retrieveAsyncJobResult(
      String jobId, TypeReference<T> typeReference) {
    return retrieveAsyncJobResult(jobId, /* resultClass= */ null, typeReference);
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
      String jobId, Class<T> resultClass, TypeReference<T> typeReference) {
    try {
      FlightState flightState = jobService.retrieveJob(jobId);
      ApiJobReport jobReport = mapFlightStateToApiJobReport(flightState);
      if (jobReport.getStatus().equals(ApiJobReport.StatusEnum.RUNNING)) {
        return new AsyncJobResult<T>().jobReport(jobReport);
      }

      // Job is complete, get the result
      JobService.JobResultOrException<T> resultOrException =
          jobService.retrieveJobResult(jobId, resultClass, typeReference);
      final ApiErrorReport errorReport;
      if (jobReport.getStatus().equals(ApiJobReport.StatusEnum.FAILED)) {
        errorReport = ErrorReportUtils.buildApiErrorReport(resultOrException.getException());
      } else {
        errorReport = null;
      }
      return new AsyncJobResult<T>()
          .jobReport(jobReport)
          .result(resultOrException.getResult())
          .errorReport(errorReport);
    } catch (StairwayException stairwayEx) {
      throw new InternalStairwayException(stairwayEx);
    }
  }

  public ApiJobReport mapFlightStateToApiJobReport(FlightState flightState) {
    FlightMap inputParameters = flightState.getInputParameters();
    String description = inputParameters.get(JobMapKeys.DESCRIPTION.getKeyName(), String.class);
    FlightStatus flightStatus = flightState.getFlightStatus();
    String submittedDate = flightState.getSubmitted().toString();
    ApiJobReport.StatusEnum jobStatus = mapFlightStatusToApi(flightStatus);

    String completedDate = null;
    HttpStatus statusCode = HttpStatus.ACCEPTED;

    if (jobStatus != ApiJobReport.StatusEnum.RUNNING) {
      // If the job is completed, the JobReport should include a result code indicating success or
      // failure. For failed jobs, this code is the error code. For successful jobs, this is the
      // code specified by the flight if present, or a default of 200 if not.
      completedDate =
          flightState
              .getCompleted()
              .map(Instant::toString)
              .orElseThrow(
                  () -> new InvalidResultStateException("No completed time for completed flight"));
      switch (jobStatus) {
        case FAILED -> {
          int errorCode =
              flightState
                  .getException()
                  .map(e -> ErrorReportUtils.buildApiErrorReport(e).getStatusCode())
                  .orElseThrow(
                      () ->
                          new InvalidResultStateException(
                              String.format(
                                  "Flight %s failed with no exception reported",
                                  flightState.getFlightId())));
          statusCode = HttpStatus.valueOf(errorCode);
        }
        case SUCCEEDED -> {
          FlightMap resultMap =
              flightState.getResultMap().orElseThrow(InvalidResultStateException::noResultMap);
          statusCode = resultMap.get(JobMapKeys.STATUS_CODE.getKeyName(), HttpStatus.class);
          if (statusCode == null) {
            statusCode = HttpStatus.OK;
          }
        }
        default ->
            throw new IllegalStateException(
                "Cannot get status code of flight in unknown state " + jobStatus);
      }
    }

    return new ApiJobReport()
        .id(flightState.getFlightId())
        .description(description)
        .status(jobStatus)
        .statusCode(statusCode.value())
        .submitted(submittedDate)
        .completed(completedDate)
        .resultURL(resultUrlFromFlightState(flightState));
  }

  public ApiJobResult fetchJobResult(String jobId) {
    AsyncJobResult<Void> jobResult =
        retrieveAsyncJobResult(jobId, /* resultClass= */ null, /* typeReference= */ null);
    return new ApiJobResult()
        .jobReport(jobResult.getJobReport())
        .errorReport(jobResult.getApiErrorReport());
  }

  private ApiJobReport.StatusEnum mapFlightStatusToApi(FlightStatus flightStatus) {
    switch (flightStatus) {
      case RUNNING:
      case QUEUED:
      case WAITING:
      case READY:
      case READY_TO_RESTART:
        return ApiJobReport.StatusEnum.RUNNING;
      case SUCCESS:
        return ApiJobReport.StatusEnum.SUCCEEDED;
      case ERROR:
      case FATAL:
      default:
        return ApiJobReport.StatusEnum.FAILED;
    }
  }

  private String resultUrlFromFlightState(FlightState flightState) {
    String resultPath =
        flightState.getInputParameters().get(JobMapKeys.RESULT_PATH.getKeyName(), String.class);
    if (resultPath == null) {
      resultPath = "";
    }
    // This is a little hacky, but GCP rejects non-https traffic and a local server does not
    // support it.
    String protocol =
        ingressConfig.getDomainName().startsWith("localhost") ? "http://" : "https://";
    return protocol + Path.of(ingressConfig.getDomainName(), resultPath);
  }

  /**
   * The API result of an asynchronous job is a ApiJobReport and exactly one of a job result of an
   * ApiErrorReport. If the job is incomplete, only jobReport will be present.
   *
   * @param <T> Class of the result object
   */
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
}
