package bio.terra.workspace.amalgam.landingzone.azure;

import bio.terra.common.exception.BadRequestException;

public class LandingZoneUnsupportedPurposeException extends BadRequestException {
  public LandingZoneUnsupportedPurposeException(String message) {
    super(message);
  }
}
