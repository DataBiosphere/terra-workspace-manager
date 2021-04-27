package bio.terra.workspace.service.datarepo.exception;

import bio.terra.common.exception.ForbiddenException;

public class DataRepoAuthorizationException extends ForbiddenException {
  public DataRepoAuthorizationException(String message) {
    super(message);
  }

  public DataRepoAuthorizationException(String message, Throwable cause) {
    super(message, cause);
  }

  public DataRepoAuthorizationException(Throwable cause) {
    super(cause);
  }
}
