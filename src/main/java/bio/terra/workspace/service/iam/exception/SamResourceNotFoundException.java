package bio.terra.workspace.service.iam.exception;

import bio.terra.workspace.common.exception.NotFoundException;

public class SamResourceNotFoundException extends NotFoundException {

  public SamResourceNotFoundException(Throwable cause) {
    super(cause);
  }
}
