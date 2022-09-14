package bio.terra.workspace.app.configuration.external;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties
@ConfigurationProperties(prefix = "workspace.azure")
public class AzureConfiguration {
  // Managed app authentication
  private String managedAppClientId;
  private String managedAppClientSecret;
  private String managedAppTenantId;
  private Long sasTokenStartTimeMinutesOffset;
  private Long sasTokenExpiryTimeMinutesOffset;
  private String corsOrigins;

  public String getManagedAppClientId() {
    return managedAppClientId;
  }

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

  public void setManagedAppTenantId(String managedAppTenantId) {
    this.managedAppTenantId = managedAppTenantId;
  }

  public Long getSasTokenStartTimeMinutesOffset() {
    return sasTokenStartTimeMinutesOffset;
  }

  public void setSasTokenStartTimeMinutesOffset(Long sasTokenStartTimeMinutesOffset) {
    this.sasTokenStartTimeMinutesOffset = sasTokenStartTimeMinutesOffset;
  }

  public Long getSasTokenExpiryTimeMinutesOffset() {
    return sasTokenExpiryTimeMinutesOffset;
  }

  public void setSasTokenExpiryTimeMinutesOffset(Long sasTokenExpiryTimeMinutesOffset) {
    this.sasTokenExpiryTimeMinutesOffset = sasTokenExpiryTimeMinutesOffset;
  }

  public String getCorsOrigins() {
    return corsOrigins;
  }

  public void setCorsOrigins(String corsOrigins) {
    this.corsOrigins = corsOrigins;
  }
}
