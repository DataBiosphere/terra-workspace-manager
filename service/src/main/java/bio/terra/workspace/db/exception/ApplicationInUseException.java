package bio.terra.workspace.db.exception;

import bio.terra.common.exception.NotFoundException;

public class ApplicationInUseException extends NotFoundException {

  public ApplicationInUseException(String message) {
    super(message);
  }
}
