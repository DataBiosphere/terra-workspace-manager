package bio.terra.workspace.common.exception;

/**
 * Exception thrown for serialization errors. Generally, this indicates that a code change has
 * broken serialization or deserialization.
 */
public class InconsistentFieldsException extends InternalServerErrorException {
  public InconsistentFieldsException(String message) {
    super(message);
  }

  public InconsistentFieldsException(String message, Throwable cause) {
    super(message, cause);
  }
}
