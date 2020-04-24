package bio.terra.workspace.common.exception;

import java.util.List;

public class ValidationException extends BadRequestException {

  public ValidationException(String message) {
    super(message);
  }

  public ValidationException(String message, List<String> errors) {
    super(message, errors);
  }
}
