package bio.terra.workspace.common.exception;

import bio.terra.common.exception.InternalServerErrorException;

public class UnknownFlightClassNameException extends InternalServerErrorException {

  public UnknownFlightClassNameException(String message) {
    super(message);
  }
}
