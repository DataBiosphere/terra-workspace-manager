package bio.terra.workspace.service.iam.exception;

import bio.terra.common.exception.BadRequestException;

public class InvalidRoleException extends BadRequestException {

  public InvalidRoleException(String message) {
    super(message);
  }
}
