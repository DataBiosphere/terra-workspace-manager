package bio.terra.workspace.db.exception;

import bio.terra.common.exception.ConflictException;
import java.util.List;

public class ResourceStateConflictException extends ConflictException {

  public ResourceStateConflictException(String message) {
    super(message);
  }

  public ResourceStateConflictException(String message, Throwable cause, List<String> causes) {
    super(message, cause, causes);
  }
}
