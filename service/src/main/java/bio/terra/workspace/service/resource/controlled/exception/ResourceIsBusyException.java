package bio.terra.workspace.service.resource.controlled.exception;

import bio.terra.common.exception.ConflictException;

/**
 * Exception thrown when user-specified metadata keys for notebooks conflict with keys which are
 * reserved for the Terra system.
 */
public class ResourceIsBusyException extends ConflictException {

  public ResourceIsBusyException(String message) {
    super(message);
  }
}
