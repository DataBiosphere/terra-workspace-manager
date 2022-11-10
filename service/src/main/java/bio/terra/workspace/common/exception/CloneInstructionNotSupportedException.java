package bio.terra.workspace.common.exception;

import bio.terra.common.exception.NotImplementedException;
import java.util.List;

public class CloneInstructionNotSupportedException extends NotImplementedException {

  public CloneInstructionNotSupportedException(String message) {
    super(message);
  }

  public CloneInstructionNotSupportedException(String message, Throwable cause) {
    super(message, cause);
  }

  public CloneInstructionNotSupportedException(Throwable cause) {
    super(cause);
  }

  public CloneInstructionNotSupportedException(String message, List<String> causes) {
    super(message, causes);
  }

  public CloneInstructionNotSupportedException(
      String message, Throwable cause, List<String> causes) {
    super(message, cause, causes);
  }
}
