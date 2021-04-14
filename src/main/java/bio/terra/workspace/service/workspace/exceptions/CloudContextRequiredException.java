package bio.terra.workspace.service.workspace.exceptions;

import bio.terra.workspace.common.exception.BadRequestException;

/** When you can't get there from here, but somehow end up there */
public class CloudContextRequiredException extends BadRequestException {
  public CloudContextRequiredException(String message) {
    super(message);
  }
}
