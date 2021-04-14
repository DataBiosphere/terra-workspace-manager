package bio.terra.workspace.db.exception;

import bio.terra.common.exception.BadRequestException;

public class InvalidWorkspaceStageException extends BadRequestException {
  public InvalidWorkspaceStageException(String message) {
    super(message);
  }
}
