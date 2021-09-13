package bio.terra.workspace.app.configuration.external;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/** Azure POC code */
@EnableConfigurationProperties
@EnableTransactionManagement
@ConfigurationProperties(prefix = "workspace.azure")
public class AzureConfiguration {

  /** tenant where WSM managed app is deployed */
  private String tenantId;

  /** MRG id */
  private String managedResourceGroupId;

  /** clientId for access to the MRG */
  private String clientId;

  /** clientSecret for access to the MRG */
  private String clientSecret;

  public String getTenantId() {
    return tenantId;
  }

  public void setTenantId(String tenantId) {
    this.tenantId = tenantId;
  }

  public String getManagedResourceGroupId() {
    return managedResourceGroupId;
  }

  public void setManagedResourceGroupId(String managedResourceGroupId) {
    this.managedResourceGroupId = managedResourceGroupId;
  }

  public String getClientId() {
    return clientId;
  }

  public void setClientId(String clientId) {
    this.clientId = clientId;
  }

  public String getClientSecret() {
    return clientSecret;
  }

  public void setClientSecret(String clientSecret) {
    this.clientSecret = clientSecret;
  }
}
