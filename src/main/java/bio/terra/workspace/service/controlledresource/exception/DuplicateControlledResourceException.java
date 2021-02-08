package bio.terra.workspace.service.controlledresource.exception;

import bio.terra.workspace.common.exception.ConflictException;
import java.util.List;

public class DuplicateControlledResourceException extends ConflictException {

  public DuplicateControlledResourceException(String message) {
    super(message);
  }

  public DuplicateControlledResourceException(String message, Throwable cause) {
    super(message, cause);
  }

  public DuplicateControlledResourceException(Throwable cause) {
    super(cause);
  }

  public DuplicateControlledResourceException(String message, List<String> causes) {
    super(message, causes);
  }

  public DuplicateControlledResourceException(
      String message, Throwable cause, List<String> causes) {
    super(message, cause, causes);
  }
}
