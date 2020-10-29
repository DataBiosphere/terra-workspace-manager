package bio.terra.workspace.db.exception;

import bio.terra.workspace.common.exception.BadRequestException;
import java.util.List;

public class InvalidWorkspaceStageException extends BadRequestException {
  public InvalidWorkspaceStageException(String message) {
    super(message);
  }

  public InvalidWorkspaceStageException(String message, Throwable cause) {
    super(message, cause);
  }

  public InvalidWorkspaceStageException(Throwable cause) {
    super(cause);
  }

  public InvalidWorkspaceStageException(String message, List<String> causes) {
    super(message, causes);
  }

  public InvalidWorkspaceStageException(String message, Throwable cause, List<String> causes) {
    super(message, cause, causes);
  }
}
