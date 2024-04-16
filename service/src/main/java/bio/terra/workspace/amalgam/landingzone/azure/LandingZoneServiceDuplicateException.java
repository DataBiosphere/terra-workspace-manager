package bio.terra.workspace.amalgam.landingzone.azure;

import bio.terra.common.exception.BadRequestException;

public class LandingZoneServiceDuplicateException extends BadRequestException {

  public LandingZoneServiceDuplicateException(String message) {
    super(message);
  }

  public LandingZoneServiceDuplicateException(String message, Throwable cause) {
    super(message, cause);
  }
}
