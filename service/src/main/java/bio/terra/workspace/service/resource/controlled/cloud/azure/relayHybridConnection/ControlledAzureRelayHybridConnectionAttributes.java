package bio.terra.workspace.service.resource.controlled.cloud.azure.relayHybridConnection;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class ControlledAzureRelayHybridConnectionAttributes {
  private final String namespaceName;
  private final String hybridConnectionName;
  private final Boolean requiresClientAuthorization;

  @JsonCreator
  public ControlledAzureRelayHybridConnectionAttributes(
      @JsonProperty("namespaceName") String namespaceName, @JsonProperty("hybridConnectionName") String hybridConnectionName, @JsonProperty("requiresClientAuthorization") Boolean requiresClientAuthorization) {
    this.namespaceName = namespaceName;
    this.hybridConnectionName = hybridConnectionName;
    this.requiresClientAuthorization = requiresClientAuthorization;
  }

  public String getNamespaceName() {
    return namespaceName;
  }

  public String getHybridConnectionName() {
    return hybridConnectionName;
  }

  public Boolean isRequiresClientAuthorization() {
    return requiresClientAuthorization;
  }
}
