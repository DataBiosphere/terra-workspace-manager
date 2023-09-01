package bio.terra.workspace.service.resource.controlled.cloud.azure.kubernetesNamespace;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Set;
import java.util.UUID;

public class ControlledAzureKubernetesNamespaceAttributes {
  private final String kubernetesNamespace;
  private final String kubernetesServiceAccount;
  private final UUID managedIdentity;
  private final Set<UUID> databases;

  @JsonCreator
  public ControlledAzureKubernetesNamespaceAttributes(
      @JsonProperty("kubernetesNamespace") String kubernetesNamespace,
      @JsonProperty("kubernetesServiceAccount") String kubernetesServiceAccount,
      @JsonProperty("managedIdentity") UUID managedIdentity,
      @JsonProperty("databases") Set<UUID> databases) {
    this.kubernetesNamespace = kubernetesNamespace;
    this.kubernetesServiceAccount = kubernetesServiceAccount;
    this.managedIdentity = managedIdentity;
    this.databases = databases;
  }

  public String getKubernetesNamespace() {
    return kubernetesNamespace;
  }

  public String getKubernetesServiceAccount() {
    return kubernetesServiceAccount;
  }

  public UUID getManagedIdentity() {
    return managedIdentity;
  }

  public Set<UUID> getDatabases() {
    return databases;
  }
}
