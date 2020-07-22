package bio.terra.workspace.app.configuration.exceptions;

import bio.terra.workspace.common.exception.BadRequestException;
import java.util.List;

public class InputJsonParsingException extends BadRequestException {

  public InputJsonParsingException(String message) {
    super(message);
  }

  public InputJsonParsingException(String message, Throwable cause) {
    super(message, cause);
  }

  public InputJsonParsingException(Throwable cause) {
    super(cause);
  }

  public InputJsonParsingException(String message, List<String> causes) {
    super(message, causes);
  }

  public InputJsonParsingException(String message, Throwable cause, List<String> causes) {
    super(message, cause, causes);
  }
}
