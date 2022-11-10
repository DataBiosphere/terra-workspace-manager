package bio.terra.workspace.amalgam.landingzone.azure;

import bio.terra.common.exception.NotFoundException;

public class LandingZoneNotFoundException extends NotFoundException {
  public LandingZoneNotFoundException(String message) {
    super(message);
  }
}
