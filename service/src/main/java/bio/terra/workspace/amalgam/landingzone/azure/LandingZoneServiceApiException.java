package bio.terra.workspace.amalgam.landingzone.azure;

import bio.terra.buffer.client.ApiException;
import bio.terra.common.exception.ErrorReportException;
import java.util.Collections;
import java.util.List;
import org.springframework.http.HttpStatus;

public class LandingZoneServiceApiException extends ErrorReportException {
  private ApiException apiException;

  public LandingZoneServiceApiException(ApiException ex) {
    super(
        "Error from Landing Zone Service: ",
        ex,
        Collections.singletonList(ex.getResponseBody()),
        HttpStatus.resolve(ex.getCode()));
    this.apiException = ex;
  }

  public LandingZoneServiceApiException(String message) {
    super(message);
  }

  public LandingZoneServiceApiException(String message, Throwable cause) {
    super(message, cause);
  }

  public LandingZoneServiceApiException(Throwable cause) {
    super(cause);
  }

  public LandingZoneServiceApiException(
      String message, List<String> causes, HttpStatus statusCode) {
    super(message, causes, statusCode);
  }

  public LandingZoneServiceApiException(
      String message, Throwable cause, List<String> causes, HttpStatus statusCode) {
    super(message, cause, causes, statusCode);
  }

  /** Get the HTTP status code of the underlying response from Policy Service. */
  public int getApiExceptionStatus() {
    return apiException.getCode();
  }
}
