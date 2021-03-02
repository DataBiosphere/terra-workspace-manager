package bio.terra.workspace.service.resource.exception;

import bio.terra.workspace.common.exception.NotFoundException;

public class ResourceNotFoundException extends NotFoundException {

  public ResourceNotFoundException(String message) {
    super(message);
  }
}
