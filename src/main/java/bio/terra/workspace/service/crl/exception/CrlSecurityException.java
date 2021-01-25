package bio.terra.workspace.service.crl.exception;

import bio.terra.workspace.common.exception.ForbiddenException;

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
