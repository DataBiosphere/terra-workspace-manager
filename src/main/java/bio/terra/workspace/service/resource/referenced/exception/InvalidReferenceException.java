package bio.terra.workspace.service.resource.referenced.exception;

import bio.terra.workspace.common.exception.BadRequestException;

public class InvalidReferenceException extends BadRequestException {

  public InvalidReferenceException(String message) {
    super(message);
  }

  public InvalidReferenceException(String message, Throwable cause) {
    super(message, cause);
  }

  public InvalidReferenceException(Throwable cause) {
    super(cause);
  }
}
