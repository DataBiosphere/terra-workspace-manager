package bio.terra.workspace.service.resource.controlled.exception;

import bio.terra.common.exception.InternalServerErrorException;

public class AzureNetworkInterfaceNameNotFoundException extends InternalServerErrorException {
  public AzureNetworkInterfaceNameNotFoundException(String message) {
    super(message);
  }
}
