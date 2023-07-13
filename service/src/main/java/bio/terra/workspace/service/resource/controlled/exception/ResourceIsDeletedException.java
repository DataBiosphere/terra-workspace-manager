package bio.terra.workspace.service.resource.controlled.exception;

import bio.terra.common.exception.InternalServerErrorException;

/**
 * Resource has been deleted and flight steps that delete resources will throw this except in its
 * undo step
 */
public class ResourceIsDeletedException extends InternalServerErrorException {

  public ResourceIsDeletedException(String message) {
    super(message);
  }

  public ResourceIsDeletedException(Throwable cause) {
    super(cause);
  }
}
