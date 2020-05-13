package bio.terra.workspace.common.exception;

public class DuplicateDataReferenceException extends ConflictException {

  public DuplicateDataReferenceException(String message) {
    super(message);
  }

  public DuplicateDataReferenceException(String message, Throwable cause) {
    super(message, cause);
  }
}
