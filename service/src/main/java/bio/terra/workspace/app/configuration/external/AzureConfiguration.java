package bio.terra.workspace.app.configuration.external;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Azure POC code */
@Configuration
@EnableConfigurationProperties
@ConfigurationProperties(prefix = "workspace.azure")
public class AzureConfiguration {
  // Managed app authentication
  private String managedAppClientId;
  private String managedAppClientSecret;
  private String managedAppTenantId;

  private String customDockerImageId;

  public void setManagedAppClientId(String managedAppClientId) {
    this.managedAppClientId = managedAppClientId;
  }

  public String getManagedAppClientSecret() {
    return managedAppClientSecret;
  }

  public void setManagedAppClientSecret(String managedAppClientSecret) {
    this.managedAppClientSecret = managedAppClientSecret;
  }

  public String getManagedAppTenantId() {
    return managedAppTenantId;
  }

  public String getManagedAppClientId() {
    return managedAppClientId;
  }

  public void setManagedAppTenantId(String managedAppTenantId) {
    this.managedAppTenantId = managedAppTenantId;
  }

  public String getCustomDockerImageId() {
    return customDockerImageId;
  }

  public void setCustomDockerImageId(String customDockerImageId) {
    this.customDockerImageId = customDockerImageId;
  }
}
