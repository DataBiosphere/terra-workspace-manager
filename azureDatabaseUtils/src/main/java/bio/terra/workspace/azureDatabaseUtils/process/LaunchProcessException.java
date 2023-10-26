package bio.terra.workspace.azureDatabaseUtils.process;

/**
 * Custom exception class for system or internal exceptions. These represent errors that the user
 * cannot fix.
 */
public class LaunchProcessException extends RuntimeException {
  /**
   * Constructs an exception with the given message. The cause is set to null.
   *
   * @param message description of error that may help with debugging
   */
  public LaunchProcessException(String message) {
    super(message);
  }

  /**
   * Constructs an exception with the given message and cause.
   *
   * @param message description of error that may help with debugging
   * @param cause underlying exception that can be logged for debugging purposes
   */
  public LaunchProcessException(String message, Throwable cause) {
    super(message, cause);
  }
}
