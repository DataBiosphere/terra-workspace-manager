package bio.terra.workspace.integration.common.configuration;

import java.util.HashMap;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties
@ConfigurationProperties(prefix = "it")
public class TestConfiguration {

  private HashMap<String, String> wsmUrls;
  private HashMap<String, String> wsmEndpoints;
  private String serviceAccountEmail;
  private String serviceAccountFilePath;

  public void setWsmEndpoints(HashMap<String, String> wsmEndpoints) {
    this.wsmEndpoints = wsmEndpoints;
  }

  public void setWsmUrls(HashMap<String, String> wsmUrls) {
    this.wsmUrls = wsmUrls;
  }

  public String getWsmCreateWorkspaceUrl() {
    return this.wsmUrls.get("dev") + this.wsmEndpoints.get("createWorkspace");
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
