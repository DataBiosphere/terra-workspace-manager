package bio.terra.workspace.service.workspace.exceptions;

import bio.terra.common.exception.InternalServerErrorException;

public class SaCredentialsMissingException extends InternalServerErrorException {

  public SaCredentialsMissingException(String message) {
    super(message);
  }
}
