package bio.terra.workspace.service.job.exception;

import bio.terra.workspace.common.exception.InternalServerErrorException;

public class InvalidResultStateException extends InternalServerErrorException {
  public InvalidResultStateException(String message) {
    super(message);
  }

  public InvalidResultStateException(String message, Throwable cause) {
    super(message, cause);
  }

  public InvalidResultStateException(Throwable cause) {
    super(cause);
  }
}
