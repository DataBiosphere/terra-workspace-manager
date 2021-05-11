package bio.terra.workspace.common.utils;

import javax.servlet.http.HttpServletRequest;

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
    return String.format("%s/%s/%s", request.getServletPath(), jobId, resultWord);
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

  private ControllerUtils() {}
}
