package bio.terra.workspace.service.workspace.exceptions;

import bio.terra.common.exception.InternalServerErrorException;

/** Exception thrown when multiple concurrent flights create a conflict which they cannot handle. */
public class ConcurrentFlightModificationException extends InternalServerErrorException {

  public ConcurrentFlightModificationException(String message) {
    super(message);
  }
}
