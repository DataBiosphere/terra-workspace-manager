package bio.terra.workspace.db.exception;

import javax.ws.rs.InternalServerErrorException;

public class UnknownFlightOperationTypeException extends InternalServerErrorException {

  public UnknownFlightOperationTypeException(String message) {
    super(message);
  }
}
