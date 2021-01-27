package bio.terra.workspace.service.crl.exception;

import bio.terra.workspace.common.exception.InternalServerErrorException;

/** An attempt was made to use the Cloud Resource Library, but it is set not to be used. */
public class CrlNotInUseException extends InternalServerErrorException {

  public CrlNotInUseException(String message) {
    super(message);
  }

  public CrlNotInUseException(String message, Throwable cause) {
    super(message, cause);
  }

  public CrlNotInUseException(Throwable cause) {
    super(cause);
  }
}
