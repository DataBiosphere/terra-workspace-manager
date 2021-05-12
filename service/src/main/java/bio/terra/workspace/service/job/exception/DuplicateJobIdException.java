package bio.terra.workspace.service.job.exception;

import bio.terra.common.exception.ConflictException;

/** An exception indicating a jobId is already in use. Error code is 409 CONFLICT. */
public class DuplicateJobIdException extends ConflictException {
  public DuplicateJobIdException(String message, Throwable cause) {
    super(message, cause);
  }
}
