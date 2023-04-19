package bio.terra.workspace.service.resource.controlled.exception;

import bio.terra.common.exception.InternalServerErrorException;

public class AwsBucketNotFoundException extends InternalServerErrorException {
  public AwsBucketNotFoundException(String message, Throwable cause) {
    super(message, cause);
  }
}
