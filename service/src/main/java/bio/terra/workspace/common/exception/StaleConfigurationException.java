package bio.terra.workspace.common.exception;

import bio.terra.common.exception.InternalServerErrorException;

public class StaleConfigurationException extends InternalServerErrorException {
  public StaleConfigurationException(String message) {
    super(message);
  }

  public StaleConfigurationException(String message, Throwable e) {
    super(message, e);
  }
}
