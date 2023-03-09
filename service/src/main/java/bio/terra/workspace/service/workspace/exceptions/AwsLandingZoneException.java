package bio.terra.workspace.service.workspace.exceptions;

import bio.terra.common.exception.BadRequestException;

public class AwsLandingZoneException extends BadRequestException {
  public AwsLandingZoneException(String message) {
    super(message);
  }
}
