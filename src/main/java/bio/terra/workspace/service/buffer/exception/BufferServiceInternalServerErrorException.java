package bio.terra.workspace.service.buffer.exception;

import bio.terra.workspace.common.exception.InternalServerErrorException;

public class BufferServiceInternalServerErrorException extends InternalServerErrorException {

  public BufferServiceInternalServerErrorException(String message) {
    super(message);
  }

  public BufferServiceInternalServerErrorException(String message, Throwable cause) {
    super(message, cause);
  }

  public BufferServiceInternalServerErrorException(Throwable cause) {
    super(cause);
  }
}
