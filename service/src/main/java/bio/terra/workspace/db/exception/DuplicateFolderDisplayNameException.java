package bio.terra.workspace.db.exception;

import javax.ws.rs.BadRequestException;

public class DuplicateFolderDisplayNameException extends BadRequestException {
  public DuplicateFolderDisplayNameException(String message) {
    super(message);
  }
}
