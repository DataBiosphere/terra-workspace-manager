package bio.terra.workspace.service.resource.controlled.azure.ip;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class ControlledAzureIpAttributes {
  private final String ipName;
  private final String region;

  @JsonCreator
  public ControlledAzureIpAttributes(
      @JsonProperty("ipName") String ipName, @JsonProperty("region") String region) {
    this.ipName = ipName;
    this.region = region;
  }

  public String getIpName() {
    return ipName;
  }

  public String getRegion() {
    return region;
  }
}
