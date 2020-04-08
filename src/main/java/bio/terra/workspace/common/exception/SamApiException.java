package bio.terra.workspace.common.exception;

import java.util.Collections;
import java.util.List;
import org.broadinstitute.dsde.workbench.client.sam.ApiException;
import org.springframework.http.HttpStatus;

public class SamApiException extends ErrorReportException {

  public SamApiException(ApiException samException) {
    super(
        "Error from SAM: ",
        Collections.singletonList(samException.getResponseBody()),
        HttpStatus.resolve(samException.getCode()));
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
}
