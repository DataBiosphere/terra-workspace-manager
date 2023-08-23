package bio.terra.workspace.common.exception;

import bio.terra.common.exception.BadRequestException;

public class InvalidRemoveUserRequestException extends BadRequestException {

  public InvalidRemoveUserRequestException(String message) {
    super(message);
  }
}
