package bio.terra.workspace.service.resource.controlled.exception;

import bio.terra.common.exception.BadRequestException;

public class RegionNotAllowedException extends BadRequestException {

  public RegionNotAllowedException(String message) {
    super(message);
  }

  public RegionNotAllowedException(String message, Throwable cause) {
    super(message, cause);
  }

  public RegionNotAllowedException(Throwable cause) {
    super(cause);
  }
}
