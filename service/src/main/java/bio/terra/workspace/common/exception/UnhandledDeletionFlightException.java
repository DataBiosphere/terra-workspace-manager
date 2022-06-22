package bio.terra.workspace.common.exception;

import javax.ws.rs.InternalServerErrorException;

public class UnhandledDeletionFlightException extends InternalServerErrorException {
  public UnhandledDeletionFlightException(String message) {
    super(message);
  }
}
