package bio.terra.workspace.service.buffer.exception;

import bio.terra.workspace.common.exception.UnauthorizedException;

public class BufferServiceAuthorizationException extends UnauthorizedException {

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
