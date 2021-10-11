package bio.terra.workspace.service.workspace.exceptions;

import bio.terra.common.exception.BadRequestException;

/** Exception for badly formed WSM application configurations */
public class InvalidApplicationConfigException extends BadRequestException {
  public InvalidApplicationConfigException(String message) {
    super(message);
  }
}
