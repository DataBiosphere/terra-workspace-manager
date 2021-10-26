package bio.terra.workspace.service.resource.exception;

import bio.terra.common.exception.BadRequestException;

/** An error thrown when a user provides an invalid name for a resource. */
public class InvalidNameException extends BadRequestException {

  public InvalidNameException(String message) {
    super(message);
  }
}
