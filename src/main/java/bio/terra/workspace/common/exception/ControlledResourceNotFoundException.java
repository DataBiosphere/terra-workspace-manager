package bio.terra.workspace.common.exception;

import java.util.List;

public class ControlledResourceNotFoundException extends NotFoundException {

  public ControlledResourceNotFoundException(String message) {
    super(message);
  }

  public ControlledResourceNotFoundException(String message, Throwable cause) {
    super(message, cause);
  }

  public ControlledResourceNotFoundException(Throwable cause) {
    super(cause);
  }

  public ControlledResourceNotFoundException(String message, List<String> causes) {
    super(message, causes);
  }

  public ControlledResourceNotFoundException(String message, Throwable cause, List<String> causes) {
    super(message, cause, causes);
  }
}
