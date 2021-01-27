package bio.terra.workspace.service.crl.exception;

import bio.terra.workspace.common.exception.ForbiddenException;

/** Runtime exception to propagate CRL object creation security failure */
public class CrlSecurityException extends ForbiddenException {

  public CrlSecurityException(String message) {
    super(message);
  }

  public CrlSecurityException(String message, Throwable cause) {
    super(message, cause);
  }

  public CrlSecurityException(Throwable cause) {
    super(cause);
  }
}
