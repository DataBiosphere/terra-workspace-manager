package bio.terra.workspace.service.resource.controlled.cloud.azure.vm;

import org.springframework.retry.RetryException;

class VmExtensionInstallInProgressException extends RetryException {
  public VmExtensionInstallInProgressException(String msg) {
    super(msg);
  }
}
