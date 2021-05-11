package bio.terra.workspace.db.exception;

import bio.terra.common.exception.InternalServerErrorException;

public class InvalidDaoRequestException extends InternalServerErrorException {

  public InvalidDaoRequestException(String message) {
    super(message);
  }
}
