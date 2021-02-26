package bio.terra.workspace.service.workspace.exceptions;

import bio.terra.workspace.common.exception.ConflictException;

/**
 * Exception thrown when attempting to create a Google context in a workspace which already has one.
 */
public class DuplicateCloudContextException extends ConflictException {
  public DuplicateCloudContextException(String message) {
    super(message);
  }

  public DuplicateCloudContextException(String message, Throwable cause) {
    super(message, cause);
  }
}
