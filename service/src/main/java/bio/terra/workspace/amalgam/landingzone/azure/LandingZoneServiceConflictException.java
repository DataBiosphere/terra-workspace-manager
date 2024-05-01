package bio.terra.workspace.amalgam.landingzone.azure;

import bio.terra.common.exception.ConflictException;

public class LandingZoneServiceConflictException extends ConflictException {

  public LandingZoneServiceConflictException(String message) {
    super(message);
  }

  public LandingZoneServiceConflictException(String message, Throwable cause) {
    super(message, cause);
  }
}
