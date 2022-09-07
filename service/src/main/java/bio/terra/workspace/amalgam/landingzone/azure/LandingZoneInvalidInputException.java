package bio.terra.workspace.amalgam.landingzone.azure;

import bio.terra.common.exception.BadRequestException;

public class LandingZoneInvalidInputException extends BadRequestException {
  public LandingZoneInvalidInputException(String message) {
    super(message);
  }
}
