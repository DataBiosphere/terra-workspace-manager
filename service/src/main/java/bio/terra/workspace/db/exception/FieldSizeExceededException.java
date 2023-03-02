package bio.terra.workspace.db.exception;

import bio.terra.common.exception.InternalServerErrorException;

public class FieldSizeExceededException extends InternalServerErrorException {
  public FieldSizeExceededException(String message) {
    super(message);
  }
}
