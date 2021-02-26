package bio.terra.workspace.service.workspace.exceptions;

import bio.terra.workspace.common.exception.BadRequestException;

/**
 * Exception thrown when a user attempts to use Buffer Service in an environment where it's disabled
 * or not configured. TODO(PF-302): Remove this exception when buffer is enabled and used in all
 * environments.
 */
public class InvalidSerializedVersionException extends BadRequestException {
  public InvalidSerializedVersionException(String message) {
    super(message);
  }
}
