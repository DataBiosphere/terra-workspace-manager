package bio.terra.workspace.service.job.exception;

import bio.terra.workspace.common.exception.BadRequestException;

/** An exception indicating an invalid jobId string value. Error code is 400 Bad Request. */
public class InvalidJobIdException extends BadRequestException {
  public InvalidJobIdException(String message) {
    super(message);
  }

  public InvalidJobIdException(String message, Throwable cause) {
    super(message, cause);
  }

  public InvalidJobIdException(Throwable cause) {
    super(cause);
  }
}
