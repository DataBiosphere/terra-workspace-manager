package bio.terra.workspace.service.resource.controlled.exception;

import bio.terra.common.exception.InternalServerErrorException;

/**
 * Exception thrown when user-specified metadata keys for notebooks conflict with keys which are
 * reserved for the Terra system.
 */
public class ResourceIsCorruptException extends InternalServerErrorException {

  public ResourceIsCorruptException(String message) {
    super(message);
  }
}
