package bio.terra.workspace.service.resource.controlled.exception;

import bio.terra.workspace.common.exception.BadRequestException;

public class InvalidControlledResourceException extends BadRequestException {

  public InvalidControlledResourceException(String message) {
    super(message);
  }

  public InvalidControlledResourceException(String message, Throwable cause) {
    super(message, cause);
  }

  public InvalidControlledResourceException(Throwable cause) {
    super(cause);
  }
}
