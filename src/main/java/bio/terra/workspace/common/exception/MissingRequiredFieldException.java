package bio.terra.workspace.common.exception;

/**
 * Exception thrown for serialization errors. Generally, this indicates that a code change has
 * broken serialization or deserialization.
 */
public class MissingRequiredFieldException extends BadRequestException {
  public MissingRequiredFieldException(String message) {
    super(message);
  }

  public MissingRequiredFieldException(String message, Throwable cause) {
    super(message, cause);
  }
}
