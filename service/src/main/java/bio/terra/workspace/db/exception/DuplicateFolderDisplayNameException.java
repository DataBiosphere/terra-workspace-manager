package bio.terra.workspace.db.exception;

import bio.terra.common.exception.BadRequestException;

public class DuplicateFolderDisplayNameException extends BadRequestException {
  public DuplicateFolderDisplayNameException(String message) {
    super(message);
  }
}
