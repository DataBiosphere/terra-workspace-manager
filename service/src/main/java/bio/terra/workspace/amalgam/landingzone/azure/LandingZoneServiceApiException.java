package bio.terra.workspace.amalgam.landingzone.azure;

import bio.terra.common.exception.ErrorReportException;

public class LandingZoneServiceApiException extends ErrorReportException {
    public LandingZoneServiceApiException(String message) {
        super(message);
    }
}
