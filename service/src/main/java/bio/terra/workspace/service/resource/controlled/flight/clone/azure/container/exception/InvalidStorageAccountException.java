package bio.terra.workspace.service.resource.controlled.flight.clone.azure.container.exception;

import bio.terra.common.exception.ValidationException;

public class InvalidStorageAccountException extends ValidationException {
  public InvalidStorageAccountException(String message) {
    super(message);
  }
}
