package bio.terra.workspace.service.workspace.exceptions;

import bio.terra.common.exception.InternalServerErrorException;

public class InvalidSerializedVersionException extends InternalServerErrorException {
  public InvalidSerializedVersionException(String message) {
    super(message);
  }
}
