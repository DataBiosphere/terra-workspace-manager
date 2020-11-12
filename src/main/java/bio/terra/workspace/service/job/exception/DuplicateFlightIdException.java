package bio.terra.workspace.service.job.exception;

import bio.terra.workspace.common.exception.BadRequestException;

public class DuplicateFlightIdException extends BadRequestException {
  public DuplicateFlightIdException(String message) {
    super(message);
  }
}
