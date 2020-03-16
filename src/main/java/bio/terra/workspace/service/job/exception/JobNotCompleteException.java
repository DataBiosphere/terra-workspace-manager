package bio.terra.workspace.service.job.exception;

import bio.terra.workspace.common.exception.BadRequestException;

public class JobNotCompleteException extends BadRequestException {
  public JobNotCompleteException(String message) {
    super(message);
  }

  public JobNotCompleteException(String message, Throwable cause) {
    super(message, cause);
  }

  public JobNotCompleteException(Throwable cause) {
    super(cause);
  }
}
