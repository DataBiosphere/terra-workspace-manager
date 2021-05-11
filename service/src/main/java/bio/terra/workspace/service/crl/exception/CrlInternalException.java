package bio.terra.workspace.service.crl.exception;

import bio.terra.common.exception.InternalServerErrorException;

/** Runtime exception to propagate CRL object creation internal server failure */
public class CrlInternalException extends InternalServerErrorException {

  public CrlInternalException(String message) {
    super(message);
  }

  public CrlInternalException(String message, Throwable cause) {
    super(message, cause);
  }

  public CrlInternalException(Throwable cause) {
    super(cause);
  }
}
