package bio.terra.workspace.amalgam.tps;

import bio.terra.common.exception.BadRequestException;

public class TpsInvalidInputException extends BadRequestException {
  public TpsInvalidInputException(String message) {
    super(message);
  }
}
