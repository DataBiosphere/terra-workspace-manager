package bio.terra.workspace.service.workspace.exceptions;

import bio.terra.workspace.common.exception.InternalServerErrorException;

/**
 * Exception thrown when a user attempts to use Buffer Service in an environment where it's disabled
 * or not configured. TODO(PF-302): Remove this exception when buffer is enabled and used in all
 * environments.
 */
public class InternalLogicException extends InternalServerErrorException {
  public InternalLogicException(String message) {
    super(message);
  }
}
