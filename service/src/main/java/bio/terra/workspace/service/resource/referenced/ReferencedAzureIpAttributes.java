package bio.terra.workspace.service.resource.referenced;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class ReferencedAzureIpAttributes {
  private final String ipName;

  @JsonCreator
  public ReferencedAzureIpAttributes(@JsonProperty("ipName") String ipName) {
    this.ipName = ipName;
  }

  public String getIpName() {
    return ipName;
  }
}
