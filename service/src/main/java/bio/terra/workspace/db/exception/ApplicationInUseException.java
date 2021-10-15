package bio.terra.workspace.db.exception;

import bio.terra.common.exception.BadRequestException;

public class ApplicationInUseException extends BadRequestException {

  public ApplicationInUseException(String message) {
    super(message);
  }
}
