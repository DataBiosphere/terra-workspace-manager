package bio.terra.workspace.integration.common.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties
@ConfigurationProperties(prefix = "it")
public class TestConfiguration {

  private String wsmCreateWorkspaceUrl;
  private String serviceAccountEmail;
  private String serviceAccountFilePath;

  public String getWsmCreateWorkspaceUrl() {
    return wsmCreateWorkspaceUrl;
  }

  public void setWsmCreateWorkspaceUrl(String wsmCreateWorkspaceUrl) {
    this.wsmCreateWorkspaceUrl = wsmCreateWorkspaceUrl;
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
