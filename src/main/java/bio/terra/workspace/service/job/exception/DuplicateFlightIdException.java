package bio.terra.workspace.service.job.exception;

import bio.terra.workspace.common.exception.BadRequestException;

/** Exception indicating a flight ID was already used inside Stairway's database. */
public class DuplicateFlightIdException extends BadRequestException {
  public DuplicateFlightIdException(String message) {
    super(message);
  }
}
