package bio.terra.workspace.common.exception;

import bio.terra.common.exception.InternalServerErrorException;

public class UnhandledActivityLogException extends InternalServerErrorException {

  public UnhandledActivityLogException(String message) {
    super(message);
  }
}
