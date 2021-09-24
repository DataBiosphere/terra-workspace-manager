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
  // Test users
  private String defaultUserEmail;
  private String defaultUserObjectId;
  private String secondUserEmail;
  private String secondUserObjectId;

  // MRG coordinates
  private String tenantId;
  private String subscriptionId;
  private String managedResourceGroupId;

  // Managed app authn
  private String clientId;
  private String clientSecret;

  // -- accessors --

  public String getDefaultUserEmail() {
    return defaultUserEmail;
  }

  public void setDefaultUserEmail(String defaultUserEmail) {
    this.defaultUserEmail = defaultUserEmail;
  }

  public String getDefaultUserObjectId() {
    return defaultUserObjectId;
  }

  public void setDefaultUserObjectId(String defaultUserObjectId) {
    this.defaultUserObjectId = defaultUserObjectId;
  }

  public String getSecondUserEmail() {
    return secondUserEmail;
  }

  public void setSecondUserEmail(String secondUserEmail) {
    this.secondUserEmail = secondUserEmail;
  }

  public String getSecondUserObjectId() {
    return secondUserObjectId;
  }

  public void setSecondUserObjectId(String secondUserObjectId) {
    this.secondUserObjectId = secondUserObjectId;
  }

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
