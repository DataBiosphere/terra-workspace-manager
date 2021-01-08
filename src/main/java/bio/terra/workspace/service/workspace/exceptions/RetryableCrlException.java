package bio.terra.workspace.service.workspace.exceptions;

import bio.terra.stairway.exception.RetryException;

/** Exception for retryable errors thrown by the Cloud Resource Library (CRL).
 *
 * This extends Stairway's RetryException, which will signal Stairway to retry any step this is
 * thrown from (modulo a retry rule).
 */
public class RetryableCrlException extends RetryException {
  public RetryableCrlException(String message) {
    super(message);
  }

  public RetryableCrlException(String message, Throwable cause) {
    super(message, cause);
  }
}
