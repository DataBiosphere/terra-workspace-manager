package bio.terra.workspace.service.job.exception;

import bio.terra.common.exception.ForbiddenException;

public class JobUnauthorizedException extends ForbiddenException {
  public JobUnauthorizedException(String message) {
    super(message);
  }

  public JobUnauthorizedException(String message, Throwable cause) {
    super(message, cause);
  }

  public JobUnauthorizedException(Throwable cause) {
    super(cause);
  }
}
