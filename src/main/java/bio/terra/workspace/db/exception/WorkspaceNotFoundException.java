package bio.terra.workspace.db.exception;

import bio.terra.workspace.common.exception.NotFoundException;

public class WorkspaceNotFoundException extends NotFoundException {

  public WorkspaceNotFoundException(String message) {
    super(message);
  }
}
