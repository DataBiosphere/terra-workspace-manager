package bio.terra.workspace.common.exception;

// This base class has data that corresponds to the ApiErrorReport model generated from
// the OpenAPI yaml. The global exception handler auto-magically converts exceptions
// of this base class into the appropriate ApiErrorReport REST response.

import java.util.List;
import org.springframework.http.HttpStatus;

public abstract class BadRequestException extends ErrorReportException {
  private static final HttpStatus thisStatus = HttpStatus.BAD_REQUEST;

  public BadRequestException(String message) {
    super(message, null, thisStatus);
  }

  public BadRequestException(String message, Throwable cause) {
    super(message, cause, null, thisStatus);
  }

  public BadRequestException(Throwable cause) {
    super(null, cause, null, thisStatus);
  }

  public BadRequestException(String message, List<String> causes) {
    super(message, causes, thisStatus);
  }

  public BadRequestException(String message, Throwable cause, List<String> causes) {
    super(message, cause, causes, thisStatus);
  }
}
