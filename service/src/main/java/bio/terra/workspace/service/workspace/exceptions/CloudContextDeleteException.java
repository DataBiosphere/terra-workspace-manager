package bio.terra.workspace.service.workspace.exceptions;

import bio.terra.common.exception.BadRequestException;
import java.util.List;

public class CloudContextDeleteException extends BadRequestException {
  public CloudContextDeleteException(String message) {
    super(message);
  }

  public CloudContextDeleteException(String message, Throwable cause) {
    super(message, cause);
  }

  public CloudContextDeleteException(Throwable cause) {
    super(cause);
  }

  public CloudContextDeleteException(String message, List<String> causes) {
    super(message, causes);
  }

  public CloudContextDeleteException(String message, Throwable cause, List<String> causes) {
    super(message, cause, causes);
  }
}
