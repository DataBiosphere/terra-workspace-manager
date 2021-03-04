package bio.terra.workspace.service.iam.exception;

import bio.terra.workspace.common.exception.BadRequestException;

public class InvalidRoleException extends BadRequestException {

  public InvalidRoleException(String message) {
    super(message);
  }
}
