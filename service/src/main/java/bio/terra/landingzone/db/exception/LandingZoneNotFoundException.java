package bio.terra.landingzone.db.exception;

import bio.terra.common.exception.NotFoundException;

public class LandingZoneNotFoundException extends NotFoundException {

  public LandingZoneNotFoundException(String message) {
    super(message);
  }
}
