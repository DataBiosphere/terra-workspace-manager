package bio.terra.workspace.service.resource.exception;

import bio.terra.workspace.common.exception.ConflictException;

public class DuplicateResourceNameException extends ConflictException {

  public DuplicateResourceNameException(String message) {
    super(message);
  }

  public DuplicateResourceNameException(String message, Throwable cause) {
    super(message, cause);
  }
}
