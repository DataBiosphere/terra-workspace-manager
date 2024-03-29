package bio.terra.workspace.service.workspace.exceptions;

import bio.terra.common.exception.BadRequestException;

/** When you can't get there from here, but somehow end up there */
public class CloudContextRequiredException extends BadRequestException {
  public CloudContextRequiredException(String message) {
    super(message);
  }

  public CloudContextRequiredException(String message, Throwable throwable) {
    super(message, throwable);
  }
}
