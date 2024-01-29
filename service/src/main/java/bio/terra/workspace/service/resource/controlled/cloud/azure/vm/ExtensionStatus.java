package bio.terra.workspace.service.resource.controlled.cloud.azure.vm;

import org.apache.commons.lang3.StringUtils;

enum ExtensionStatus {
  CANCELED("Canceled"),
  CREATING("Creating"),
  FAILED("Failed"),
  SUCCEEDED("Succeeded"),
  NOT_PRESENT("NotPresent");

  private final String status;

  ExtensionStatus(String status) {
    this.status = status;
  }

  public String toString() {
    return status;
  }

  public static ExtensionStatus fromString(String status) {
    for (ExtensionStatus extensionStatus : ExtensionStatus.values()) {
      if (StringUtils.equalsIgnoreCase(extensionStatus.status, status)) {
        return extensionStatus;
      }
    }
    throw new IllegalArgumentException(
        "No enum constant " + ExtensionStatus.class + " for status " + status);
  }
}
