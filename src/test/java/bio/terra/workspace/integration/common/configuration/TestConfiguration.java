package bio.terra.workspace.integration.common.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties
@ConfigurationProperties(prefix = "it")
public class TestConfiguration {

  private String wsmWorkspaceBaseUrl;
  private String serviceAccountEmail;
  private String serviceAccountFilePath;

  public String getWsmWorkspaceBaseUrl() {
    return wsmWorkspaceBaseUrl;
  }

  public void setWsmWorkspaceBaseUrl(String wsmWorkspaceBaseUrl) {
    this.wsmWorkspaceBaseUrl = wsmWorkspaceBaseUrl;
  }

  public String getServiceAccountEmail() {
    return serviceAccountEmail;
  }

  public void setServiceAccountEmail(String serviceAccountEmail) {
    this.serviceAccountEmail = serviceAccountEmail;
  }

  public String getServiceAccountFilePath() {
    return serviceAccountFilePath;
  }

  public void setServiceAccountFilePath(String serviceAccountFilePath) {
    this.serviceAccountFilePath = serviceAccountFilePath;
  }
}
