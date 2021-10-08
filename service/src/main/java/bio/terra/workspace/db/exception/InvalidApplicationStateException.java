package bio.terra.workspace.db.exception;

import bio.terra.common.exception.BadRequestException;

public class InvalidApplicationStateException extends BadRequestException {

  public InvalidApplicationStateException(String message) {
    super(message);
  }
}
