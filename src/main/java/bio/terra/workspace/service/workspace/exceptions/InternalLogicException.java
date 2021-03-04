package bio.terra.workspace.service.workspace.exceptions;

import bio.terra.workspace.common.exception.InternalServerErrorException;

/** When you can't get there from here, but somehow end up there */
public class InternalLogicException extends InternalServerErrorException {
  public InternalLogicException(String message) {
    super(message);
  }

  public InternalLogicException(String message, Throwable e) {
    super(message, e);
  }
}
