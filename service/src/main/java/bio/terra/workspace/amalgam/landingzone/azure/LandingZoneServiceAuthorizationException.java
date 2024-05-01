package bio.terra.workspace.amalgam.landingzone.azure;

import bio.terra.common.exception.ForbiddenException;

public class LandingZoneServiceAuthorizationException extends ForbiddenException {

  public LandingZoneServiceAuthorizationException(String message) {
    super(message);
  }

  public LandingZoneServiceAuthorizationException(String message, Throwable cause) {
    super(message, cause);
  }

  public LandingZoneServiceAuthorizationException(Throwable cause) {
    super(cause);
  }
}
