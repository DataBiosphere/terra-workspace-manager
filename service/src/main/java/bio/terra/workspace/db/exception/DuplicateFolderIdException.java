package bio.terra.workspace.db.exception;

import bio.terra.common.exception.InternalServerErrorException;

public class DuplicateFolderIdException extends InternalServerErrorException {
  public DuplicateFolderIdException(String message) {
    super(message);
  }
}
