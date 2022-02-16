package bio.terra.workspace.service.resource.controlled.cloud.azure.relayNamespace;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class ControlledAzureRelayNamespaceAttributes {
  private final String namespaceName;
  private final String region;

  @JsonCreator
  public ControlledAzureRelayNamespaceAttributes(
      @JsonProperty("name") String name, @JsonProperty("region") String region) {
    this.namespaceName = name;
    this.region = region;
  }

  public String getNamespaceName() {
    return namespaceName;
  }

  public String getRegion() {
    return region;
  }
}
