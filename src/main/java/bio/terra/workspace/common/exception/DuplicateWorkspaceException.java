package bio.terra.workspace.common.exception;

public class DuplicateWorkspaceException extends BadRequestException {

  public DuplicateWorkspaceException(String message) {
    super(message);
  }

  public DuplicateWorkspaceException(String message, Throwable cause) {
    super(message, cause);
  }
}
