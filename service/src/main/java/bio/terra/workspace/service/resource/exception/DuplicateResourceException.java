package bio.terra.workspace.service.resource.exception;

import bio.terra.common.exception.ConflictException;

public class DuplicateResourceException extends ConflictException {

  public DuplicateResourceException(String message) {
    super(message);
  }

  public DuplicateResourceException(String message, Throwable cause) {
    super(message, cause);
  }
}
