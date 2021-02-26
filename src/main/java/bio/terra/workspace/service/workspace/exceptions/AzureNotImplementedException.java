package bio.terra.workspace.service.workspace.exceptions;

import bio.terra.workspace.common.exception.NotImplementedException;

/**
 * Exception thrown when a user attempts to use Buffer Service in an environment where it's disabled
 * or not configured. TODO(PF-302): Remove this exception when buffer is enabled and used in all
 * environments.
 */
public class AzureNotImplementedException extends NotImplementedException {
  public AzureNotImplementedException(String message) {
    super(message);
  }
}
