package bio.terra.workspace.service.resource.controlled.cloud.azure.hybridConnection;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class ControlledAzureHybridConnectionAttributes {
  private final String hybridConnectionName;

  @JsonCreator
  public ControlledAzureHybridConnectionAttributes(
      @JsonProperty("region") String hybridConnectionName) {
    this.hybridConnectionName = hybridConnectionName;
  }

  public String getHybridConnectionName() {
    return hybridConnectionName;
  }
}
