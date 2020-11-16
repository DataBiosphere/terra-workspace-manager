package bio.terra.workspace.service.iam.exception;

import bio.terra.workspace.common.exception.NotFoundException;

/**
 * Exception indicating a resource was not found in Sam. Useful for differentiating from other error
 * cases, as it may be treated differently.
 */
public class SamResourceNotFoundException extends NotFoundException {

  public SamResourceNotFoundException(Throwable cause) {
    super(cause);
  }
}
