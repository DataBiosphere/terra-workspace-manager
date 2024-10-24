package bio.terra.workspace.app.configuration.external;

import java.util.List;

import com.azure.core.management.AzureEnvironment;
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
  private Long sasTokenExpiryTimeMaximumMinutesOffset;
  private String corsAllowedOrigins;
  private String azureMonitorLinuxAgentVersion;
  private List<String> protectedDataLandingZoneDefs;
  private String azureDatabaseUtilImage;
  private Integer azureDatabaseUtilLogsTailLines;
  private String authTokenScope;
  private String wsmServiceManagedIdentity;
  private String azureEnvironment;

  public AzureEnvironment getAzureEnvironment() {
    try {
      return switch (azureEnvironment.toUpperCase()) {
        case "AZURE_GOV" -> AzureEnvironment.AZURE_US_GOVERNMENT;
        case "AZURE_CHINA" -> AzureEnvironment.AZURE_CHINA;
        default -> AzureEnvironment.AZURE;
      };
    } catch (IllegalArgumentException e) {
      return AzureEnvironment.AZURE;
    }
  }

  public void setAzureEnvironment(String azureEnvironment) {
    this.azureEnvironment = azureEnvironment;
  }

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

  public Long getSasTokenExpiryTimeMaximumMinutesOffset() {
    return sasTokenExpiryTimeMaximumMinutesOffset;
  }

  public void setSasTokenExpiryTimeMaximumMinutesOffset(
      Long sasTokenExpiryTimeMaximumMinutesOffset) {
    this.sasTokenExpiryTimeMaximumMinutesOffset = sasTokenExpiryTimeMaximumMinutesOffset;
  }

  public String getCorsAllowedOrigins() {
    return corsAllowedOrigins;
  }

  public void setCorsAllowedOrigins(String corsAllowedOrigins) {
    this.corsAllowedOrigins = corsAllowedOrigins;
  }

  public String getAzureMonitorLinuxAgentVersion() {
    return azureMonitorLinuxAgentVersion;
  }

  public void setAzureMonitorLinuxAgentVersion(String azureMonitorLinuxAgentVersion) {
    this.azureMonitorLinuxAgentVersion = azureMonitorLinuxAgentVersion;
  }

  public List<String> getProtectedDataLandingZoneDefs() {
    return protectedDataLandingZoneDefs;
  }

  public void setProtectedDataLandingZoneDefs(List<String> protectedDataLandingZoneDefs) {
    this.protectedDataLandingZoneDefs = protectedDataLandingZoneDefs;
  }

  public String getAzureDatabaseUtilImage() {
    return azureDatabaseUtilImage;
  }

  public void setAzureDatabaseUtilImage(String azureDatabaseUtilImage) {
    this.azureDatabaseUtilImage = azureDatabaseUtilImage;
  }

  public Integer getAzureDatabaseUtilLogsTailLines() {
    return azureDatabaseUtilLogsTailLines;
  }

  public void setAzureDatabaseUtilLogsTailLines(Integer azureDatabaseUtilLogsTailLines) {
    this.azureDatabaseUtilLogsTailLines = azureDatabaseUtilLogsTailLines;
  }

  public String getAuthTokenScope() {
    return authTokenScope;
  }

  public void setAuthTokenScope(String authTokenScope) {
    this.authTokenScope = authTokenScope;
  }

  public String getWsmServiceManagedIdentity() {
    return wsmServiceManagedIdentity;
  }

  public void setWsmServiceManagedIdentity(String wsmServiceManagedIdentity) {
    this.wsmServiceManagedIdentity = wsmServiceManagedIdentity;
  }


}
