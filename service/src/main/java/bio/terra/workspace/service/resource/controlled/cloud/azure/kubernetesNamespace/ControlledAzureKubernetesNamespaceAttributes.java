package bio.terra.workspace.service.resource.controlled.cloud.azure.kubernetesNamespace;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Set;
import java.util.UUID;

public class ControlledAzureKubernetesNamespaceAttributes {
  private final String kubernetesNamespace;
  private final String kubernetesServiceAccount;
  private final String managedIdentity;
  private final Set<String> databases;

  @JsonCreator
  public ControlledAzureKubernetesNamespaceAttributes(
      @JsonProperty("kubernetesNamespace") String kubernetesNamespace,
      @JsonProperty("kubernetesServiceAccount") String kubernetesServiceAccount,
      @JsonProperty("managedIdentity") String managedIdentity,
      @JsonProperty("databases") Set<String> databases) {
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

  public String getManagedIdentity() {
    return managedIdentity;
  }

  public Set<String> getDatabases() {
    return databases;
  }
}
