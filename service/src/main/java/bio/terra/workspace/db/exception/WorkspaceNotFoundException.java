package bio.terra.workspace.db.exception;

import bio.terra.common.exception.NotFoundException;

public class WorkspaceNotFoundException extends NotFoundException {

  public WorkspaceNotFoundException(String message) {
    super(message);
  }
}
