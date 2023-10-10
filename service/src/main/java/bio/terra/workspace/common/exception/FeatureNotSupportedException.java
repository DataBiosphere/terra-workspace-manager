package bio.terra.workspace.common.exception;

import bio.terra.common.exception.NotImplementedException;
import java.util.List;

// TODO(BENCH-1050) move exception to TCL

public class FeatureNotSupportedException extends NotImplementedException {

  public FeatureNotSupportedException(String message) {
    super(message);
  }

  public FeatureNotSupportedException(String message, Throwable cause) {
    super(message, cause);
  }

  public FeatureNotSupportedException(Throwable cause) {
    super(cause);
  }

  public FeatureNotSupportedException(String message, List<String> causes) {
    super(message, causes);
  }

  public FeatureNotSupportedException(String message, Throwable cause, List<String> causes) {
    super(message, cause, causes);
  }
}
