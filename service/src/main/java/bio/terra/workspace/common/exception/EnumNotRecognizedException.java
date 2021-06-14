package bio.terra.workspace.common.exception;

import bio.terra.common.exception.NotImplementedException;
import java.util.List;

public class EnumNotRecognizedException extends NotImplementedException {

  public EnumNotRecognizedException(String message) {
    super(message);
  }

  public EnumNotRecognizedException(String message, Throwable cause) {
    super(message, cause);
  }

  public EnumNotRecognizedException(Throwable cause) {
    super(cause);
  }

  public EnumNotRecognizedException(String message, List<String> causes) {
    super(message, causes);
  }

  public EnumNotRecognizedException(String message, Throwable cause,
      List<String> causes) {
    super(message, cause, causes);
  }
}
