package bio.terra.workspace.service.buffer.exception;

import bio.terra.common.exception.ForbiddenException;

public class BufferServiceAuthorizationException extends ForbiddenException {

  public BufferServiceAuthorizationException(String message) {
    super(message);
  }

  public BufferServiceAuthorizationException(String message, Throwable cause) {
    super(message, cause);
  }

  public BufferServiceAuthorizationException(Throwable cause) {
    super(cause);
  }
}
