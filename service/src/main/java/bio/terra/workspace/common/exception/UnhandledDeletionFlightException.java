package bio.terra.workspace.common.exception;

import jakarta.ws.rs.InternalServerErrorException;

public class UnhandledDeletionFlightException extends InternalServerErrorException {
  public UnhandledDeletionFlightException(String message) {
    super(message);
  }
}
