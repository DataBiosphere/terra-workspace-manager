package bio.terra.workspace.common.exception;

import java.util.List;

public class ResourceNotFoundException extends NotFoundException {

  public ResourceNotFoundException(String message) {
    super(message);
  }

  public ResourceNotFoundException(String message, Throwable cause) {
    super(message, cause);
  }

  public ResourceNotFoundException(Throwable cause) {
    super(cause);
  }

  public ResourceNotFoundException(String message, List<String> causes) {
    super(message, causes);
  }

  public ResourceNotFoundException(String message, Throwable cause,
      List<String> causes) {
    super(message, cause, causes);
  }
}
