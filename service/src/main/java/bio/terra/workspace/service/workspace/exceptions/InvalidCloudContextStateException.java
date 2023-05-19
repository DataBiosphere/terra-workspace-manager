package bio.terra.workspace.service.workspace.exceptions;

import bio.terra.common.exception.ConflictException;
import java.util.List;

public class InvalidCloudContextStateException extends ConflictException {
  public InvalidCloudContextStateException(String message) {
    super(message);
  }

  public InvalidCloudContextStateException(String message, Throwable cause) {
    super(message, cause);
  }

  public InvalidCloudContextStateException(Throwable cause) {
    super(cause);
  }

  public InvalidCloudContextStateException(String message, List<String> causes) {
    super(message, causes);
  }

  public InvalidCloudContextStateException(String message, Throwable cause, List<String> causes) {
    super(message, cause, causes);
  }
}
