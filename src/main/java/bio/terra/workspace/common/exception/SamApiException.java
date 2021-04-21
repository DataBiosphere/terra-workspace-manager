package bio.terra.workspace.common.exception;

import bio.terra.common.exception.ErrorReportException;
import java.util.Collections;
import java.util.List;
import org.broadinstitute.dsde.workbench.client.sam.ApiException;
import org.springframework.http.HttpStatus;

/** Wrapper exception for non-200 responses from calls to Sam. */
public class SamApiException extends ErrorReportException {

  private ApiException apiException;

  public SamApiException(ApiException samException) {
    super(
        "Error from SAM: ",
        samException,
        Collections.singletonList(samException.getResponseBody()),
        HttpStatus.resolve(samException.getCode()));
    this.apiException = samException;
  }

  public SamApiException(String message) {
    super(message);
  }

  public SamApiException(String message, Throwable cause) {
    super(message, cause);
  }

  public SamApiException(Throwable cause) {
    super(cause);
  }

  public SamApiException(String message, List<String> causes, HttpStatus statusCode) {
    super(message, causes, statusCode);
  }

  public SamApiException(
      String message, Throwable cause, List<String> causes, HttpStatus statusCode) {
    super(message, cause, causes, statusCode);
  }

  /** Get the HTTP status code of the underlying response from Sam. */
  public int getApiExceptionStatus() {
    return apiException.getCode();
  }
}
