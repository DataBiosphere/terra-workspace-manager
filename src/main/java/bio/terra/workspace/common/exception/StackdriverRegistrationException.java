package bio.terra.workspace.common.exception;

public class StackdriverRegistrationException extends ErrorReportException {

  public StackdriverRegistrationException(String message) {
    super(message);
  }

  public StackdriverRegistrationException(String message, Throwable cause) {
    super(message, cause);
  }
}
