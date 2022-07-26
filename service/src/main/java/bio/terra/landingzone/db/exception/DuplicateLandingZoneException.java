package bio.terra.landingzone.db.exception;

import bio.terra.common.exception.BadRequestException;

/** A landing zone with this landingzone_id already exists. */
public class DuplicateLandingZoneException extends BadRequestException {
  public DuplicateLandingZoneException(String message) {
    super(message);
  }

  public DuplicateLandingZoneException(String message, Throwable cause) {
    super(message, cause);
  }
}
