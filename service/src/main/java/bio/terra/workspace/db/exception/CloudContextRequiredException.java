package bio.terra.workspace.db.exception;

import bio.terra.common.exception.BadRequestException;

public class CloudContextRequiredException extends BadRequestException {
  public CloudContextRequiredException(String message) {
    super(message);
  }
}
