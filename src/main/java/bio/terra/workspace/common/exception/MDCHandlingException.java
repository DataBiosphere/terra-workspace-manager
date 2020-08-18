package bio.terra.workspace.common.exception;

import java.util.List;

public class MDCHandlingException extends InternalServerErrorException {

  public MDCHandlingException(String message) {
    super(message);
  }

  public MDCHandlingException(String message, Throwable cause) {
    super(message, cause);
  }

  public MDCHandlingException(Throwable cause) {
    super(cause);
  }

  public MDCHandlingException(String message, List<String> causes) {
    super(message, causes);
  }

  public MDCHandlingException(String message, Throwable cause, List<String> causes) {
    super(message, cause, causes);
  }
}
