package bio.terra.workspace.service.policy.exception;

import bio.terra.common.exception.BadRequestException;

public class TpsInvalidInputException extends BadRequestException {
  public TpsInvalidInputException(String message) {
    super(message);
  }
}
