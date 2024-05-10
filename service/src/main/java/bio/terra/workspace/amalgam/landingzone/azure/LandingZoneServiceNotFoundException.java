package bio.terra.workspace.amalgam.landingzone.azure;

import bio.terra.common.exception.NotFoundException;

public class LandingZoneServiceNotFoundException extends NotFoundException {

  public LandingZoneServiceNotFoundException(String message) {
    super(message);
  }

  public LandingZoneServiceNotFoundException(String message, Throwable cause) {
    super(message, cause);
  }
}
