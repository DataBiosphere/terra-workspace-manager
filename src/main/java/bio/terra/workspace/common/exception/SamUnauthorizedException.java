package bio.terra.workspace.common.exception;

import bio.terra.common.exception.ForbiddenException;

public class SamUnauthorizedException extends ForbiddenException {

  public SamUnauthorizedException(String message) {
    super(message);
  }
}
