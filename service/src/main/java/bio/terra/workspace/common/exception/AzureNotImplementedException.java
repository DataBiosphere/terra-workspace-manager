package bio.terra.workspace.common.exception;

import bio.terra.common.exception.NotImplementedException;
import java.util.List;

public class AzureNotImplementedException extends NotImplementedException {

  public AzureNotImplementedException(String message) {
    super(message);
  }

  public AzureNotImplementedException(String message, Throwable cause) {
    super(message, cause);
  }

  public AzureNotImplementedException(Throwable cause) {
    super(cause);
  }

  public AzureNotImplementedException(String message, List<String> causes) {
    super(message, causes);
  }

  public AzureNotImplementedException(String message, Throwable cause, List<String> causes) {
    super(message, cause, causes);
  }
}
