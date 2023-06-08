package bio.terra.workspace.app.configuration.external;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@Profile("azure-test")
@Component
@EnableConfigurationProperties
@EnableTransactionManagement
@ConfigurationProperties(prefix = "workspace.azure-test")
public class AzureTestConfiguration {
  // MRG coordinates
  private String tenantId;
  private String subscriptionId;
  private String managedResourceGroupId;
  private String spendProfileId;

  // -- accessors --

  public String getTenantId() {
    return tenantId;
  }

  public void setTenantId(String tenantId) {
    this.tenantId = tenantId;
  }

  public String getSubscriptionId() {
    return subscriptionId;
  }

  public void setSubscriptionId(String subscriptionId) {
    this.subscriptionId = subscriptionId;
  }

  public String getManagedResourceGroupId() {
    return managedResourceGroupId;
  }

  public void setManagedResourceGroupId(String managedResourceGroupId) {
    this.managedResourceGroupId = managedResourceGroupId;
  }

  public void setSpendProfileId(String spendProfileId) {
    this.spendProfileId = spendProfileId;
  }

  public String getSpendProfileId() {
    return spendProfileId;
  }
}
