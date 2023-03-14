package bio.terra.workspace.service.resource.controlled.exception;

import bio.terra.common.exception.InternalServerErrorException;

/** Resource is somehow corrupted and needs manual intervention */
public class ResourceIsCorruptException extends InternalServerErrorException {

  public ResourceIsCorruptException(String message) {
    super(message);
  }
}
