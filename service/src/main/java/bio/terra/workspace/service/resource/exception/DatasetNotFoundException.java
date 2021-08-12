package bio.terra.workspace.service.resource.exception;

import bio.terra.common.exception.NotFoundException;
import java.util.List;

public class DatasetNotFoundException extends NotFoundException {

  public DatasetNotFoundException(String message) {
    super(message);
  }

  public DatasetNotFoundException(String message, Throwable cause) {
    super(message, cause);
  }

  public DatasetNotFoundException(Throwable cause) {
    super(cause);
  }

  public DatasetNotFoundException(String message, List<String> causes) {
    super(message, causes);
  }

  public DatasetNotFoundException(String message, Throwable cause, List<String> causes) {
    super(message, cause, causes);
  }
}
