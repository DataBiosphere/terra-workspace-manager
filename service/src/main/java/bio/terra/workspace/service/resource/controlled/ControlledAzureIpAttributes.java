package bio.terra.workspace.service.resource.controlled;

import com.azure.core.management.Region;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class ControlledAzureIpAttributes {
  private final String ipName;
  private final Region region;

  @JsonCreator
  public ControlledAzureIpAttributes(
      @JsonProperty("ipName") String ipName, @JsonProperty("region") Region region) {
    this.ipName = ipName;
    this.region = region;
  }

  public String getIpName() {
    return ipName;
  }

  public Region getRegion() {
    return region;
  }
}
