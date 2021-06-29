package bio.terra.workspace.common.utils;

import bio.terra.workspace.generated.model.ApiJobReport;
import bio.terra.workspace.generated.model.ApiJobReport.StatusEnum;
import javax.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;

/** Class of static helper methods for controllers */
public class ControllerUtils {

  /**
   * Returns the result endpoint corresponding to an async request, prefixed with a / character. The
   * endpoint is used to build a ApiJobReport. This method generates a result endpoint with the form
   * {servletpath}/{resultWord}/{jobId} relative to the async endpoint.
   *
   * <p>Sometimes we have more than one async endpoint with the same prefix, so need to distinguish
   * them with different result words. For example, "update-result".
   *
   * @param request the servlet request
   * @param jobId the job id
   * @param resultWord the path component identifying the result
   * @return a string with the result endpoint URL
   */
  public static String getAsyncResultEndpoint(
      HttpServletRequest request, String jobId, String resultWord) {
    return String.format("%s/%s/%s", request.getServletPath(), resultWord, jobId);
  }

  /**
   * Returns the result endpoint corresponding to an async request where the desired path has the
   * form {servletpath}/result/{jobId}. Most of the time, the result word is "result".
   *
   * @param request the servlet request
   * @param jobId the job id
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
    return jobReport.getStatus() == StatusEnum.RUNNING ? HttpStatus.ACCEPTED : HttpStatus.OK;
  }

  private ControllerUtils() {}
}
