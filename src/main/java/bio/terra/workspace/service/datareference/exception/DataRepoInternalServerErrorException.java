package bio.terra.workspace.service.datareference.exception;

import bio.terra.workspace.common.exception.InternalServerErrorException;

public class DataRepoInternalServerErrorException extends InternalServerErrorException {

  public DataRepoInternalServerErrorException(String message) {
    super(message);
  }

  public DataRepoInternalServerErrorException(String message, Throwable cause) {
    super(message, cause);
  }

  public DataRepoInternalServerErrorException(Throwable cause) {
    super(cause);
  }
}
