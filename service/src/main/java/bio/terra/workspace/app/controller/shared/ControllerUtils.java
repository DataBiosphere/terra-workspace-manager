package bio.terra.workspace.app.controller.shared;

import bio.terra.workspace.generated.model.ApiJobReport;
import org.springframework.http.HttpStatus;

import javax.servlet.http.HttpServletRequest;

public class ControllerUtils {

  /**
   * Returns the result endpoint corresponding to an async request, prefixed with a / character. The
   * endpoint is used to build a ApiJobReport. This method generates a result endpoint with the form
   * {servletpath}/{resultWord}/{jobId} relative to the async endpoint.
   *
   * <p>Sometimes we have more than one async endpoint with the same prefix, so need to distinguish
   * them with different result words. For example, "update-result".
   *
   * @param jobId the job id
   * @param resultWord the path component identifying the result
   * @return a string with the result endpoint URL
   */
  public static String getAsyncResultEndpoint(HttpServletRequest request, String jobId, String resultWord) {
    return String.format("%s/%s/%s", request.getServletPath(), resultWord, jobId);
  }

  /**
   * Version of getAsyncResultEndpoint with defaulted resultWord
   * @param jobId
   * @return a string with the result endpoint URL
   */
  public static String getAsyncResultEndpoint(HttpServletRequest request, String jobId) {
    return getAsyncResultEndpoint(request, jobId, "result");
  }

  /**
   * Return the appropriate response code for an endpoint, given an async job report. For a job
   * that's still running, this is 202. For a job that's finished (either succeeded or failed), the
   * endpoint should return 200. More informational status codes will be included in either the
   * response or error report bodies.
   */
  public static HttpStatus getAsyncResponseCode(ApiJobReport jobReport) {
    return jobReport.getStatus() == ApiJobReport.StatusEnum.RUNNING ? HttpStatus.ACCEPTED : HttpStatus.OK;
  }

}
