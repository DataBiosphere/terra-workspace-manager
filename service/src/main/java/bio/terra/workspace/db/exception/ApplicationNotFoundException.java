package bio.terra.workspace.db.exception;

import bio.terra.common.exception.NotFoundException;

public class ApplicationNotFoundException extends NotFoundException {

  public ApplicationNotFoundException(String message) {
    super(message);
  }
}
