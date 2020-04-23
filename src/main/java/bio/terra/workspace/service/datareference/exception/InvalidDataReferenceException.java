package bio.terra.workspace.service.datareference.exception;

import bio.terra.workspace.common.exception.BadRequestException;

public class InvalidDataReferenceException extends BadRequestException {

  public InvalidDataReferenceException(String message) {
    super(message);
  }

  public InvalidDataReferenceException(String message, Throwable cause) {
    super(message, cause);
  }

  public InvalidDataReferenceException(Throwable cause) {
    super(cause);
  }
}
