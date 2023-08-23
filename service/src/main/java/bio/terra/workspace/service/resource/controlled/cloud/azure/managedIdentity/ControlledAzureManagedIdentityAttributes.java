package bio.terra.workspace.service.resource.controlled.cloud.azure.managedIdentity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class ControlledAzureManagedIdentityAttributes {
  private final String managedIdentityName;
  private final String region;

  @JsonCreator
  public ControlledAzureManagedIdentityAttributes(
      @JsonProperty("managedIdentityName") String managedIdentityName,
      @JsonProperty("region") String region) {
    this.managedIdentityName = managedIdentityName;
    this.region = region;
  }

  public String getManagedIdentityName() {
    return managedIdentityName;
  }

  public String getRegion() {
    return region;
  }
}
