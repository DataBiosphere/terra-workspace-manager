package bio.terra.workspace.service.resource.controlled.cloud.azure.hybridConnection;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class ControlledAzureHybridConnectionAttributes {
  private final String region;

  @JsonCreator
  public ControlledAzureHybridConnectionAttributes(
      @JsonProperty("region") String region) {
    this.region = region;
  }

  public String getRegion() {
    return region;
  }
}
