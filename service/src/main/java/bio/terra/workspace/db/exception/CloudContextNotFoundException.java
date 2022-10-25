package bio.terra.workspace.db.exception;

import bio.terra.common.exception.NotFoundException;

public class CloudContextNotFoundException extends NotFoundException {
  public CloudContextNotFoundException(String message) {
    super(message);
  }
}
