package bio.terra.workspace.db.exception;

import bio.terra.workspace.common.exception.InternalServerErrorException;

public class InvalidMetadataException extends InternalServerErrorException {

  public InvalidMetadataException(String message) {
    super(message);
  }
}
