package bio.terra.workspace.service.resource.controlled.cloud.azure.relayNamespace;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class ControlledAzureRelayNamespaceAttributes {
  private final String namespaceName;
  private final String region;

  @JsonCreator
  public ControlledAzureRelayNamespaceAttributes(
      @JsonProperty("namespaceName") String namespaceName, @JsonProperty("region") String region) {
    this.namespaceName = namespaceName;
    this.region = region;
  }

  public String getNamespaceName() {
    return namespaceName;
  }

  public String getRegion() {
    return region;
  }
}
