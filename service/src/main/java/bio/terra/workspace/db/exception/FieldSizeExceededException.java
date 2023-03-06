package bio.terra.workspace.db.exception;

import bio.terra.common.exception.BadRequestException;

public class FieldSizeExceededException extends BadRequestException {
  public FieldSizeExceededException(String message) {
    super(message);
  }
}
