package bio.terra.workspace.db.exception;

import bio.terra.common.exception.NotFoundException;

public class FolderNotFoundException extends NotFoundException {
  public FolderNotFoundException(String message) {
    super(message);
  }
}
