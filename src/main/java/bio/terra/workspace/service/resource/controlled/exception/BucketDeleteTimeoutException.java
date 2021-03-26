package bio.terra.workspace.service.resource.controlled.exception;

import bio.terra.workspace.common.exception.InternalServerErrorException;

public class BucketDeleteTimeoutException extends InternalServerErrorException {
  public BucketDeleteTimeoutException(String message) {
    super(message);
  }
}
