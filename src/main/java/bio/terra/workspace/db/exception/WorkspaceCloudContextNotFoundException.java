package bio.terra.workspace.db.exception;

import bio.terra.workspace.common.exception.NotFoundException;

public class WorkspaceCloudContextNotFoundException extends NotFoundException {

  public WorkspaceCloudContextNotFoundException(String message) {
    super(message);
  }

  public WorkspaceCloudContextNotFoundException(String message, Throwable cause) {
    super(message, cause);
  }
}
