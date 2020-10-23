package bio.terra.workspace.service.datareference.exception;

import bio.terra.workspace.common.exception.UnauthorizedException;

public class DataRepoAuthorizationException extends UnauthorizedException {
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
