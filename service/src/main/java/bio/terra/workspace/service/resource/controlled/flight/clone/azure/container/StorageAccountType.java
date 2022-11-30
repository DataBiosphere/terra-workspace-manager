package bio.terra.workspace.service.resource.controlled.flight.clone.azure.container;

public enum StorageAccountType {
  LANDING_ZONE("landing_zone"),
  WORKSPACE("workspace");

  private final String storageAccountType;

  StorageAccountType(String storageAccountType) {
    this.storageAccountType = storageAccountType;
  }
}
