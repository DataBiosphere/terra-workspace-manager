package bio.terra.workspace.service.workspace.exceptions;

import bio.terra.common.exception.ConflictException;
import java.util.List;

public class CloudContextNotReadyException extends ConflictException {
  public CloudContextNotReadyException(String message) {
    super(message);
  }

  public CloudContextNotReadyException(String message, Throwable cause) {
    super(message, cause);
  }

  public CloudContextNotReadyException(Throwable cause) {
    super(cause);
  }

  public CloudContextNotReadyException(String message, List<String> causes) {
    super(message, causes);
  }

  public CloudContextNotReadyException(String message, Throwable cause, List<String> causes) {
    super(message, cause, causes);
  }
}
