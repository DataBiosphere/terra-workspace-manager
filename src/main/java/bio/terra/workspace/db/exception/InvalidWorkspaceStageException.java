package bio.terra.workspace.db.exception;

import bio.terra.workspace.common.exception.BadRequestException;

public class InvalidWorkspaceStageException extends BadRequestException {
  public InvalidWorkspaceStageException(String message) {
    super(message);
  }
}
