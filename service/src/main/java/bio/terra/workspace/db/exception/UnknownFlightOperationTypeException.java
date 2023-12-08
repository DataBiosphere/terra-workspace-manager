package bio.terra.workspace.db.exception;

import jakarta.ws.rs.InternalServerErrorException;

public class UnknownFlightOperationTypeException extends InternalServerErrorException {

  public UnknownFlightOperationTypeException(String message) {
    super(message);
  }
}
