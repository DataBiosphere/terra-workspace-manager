package bio.terra.workspace.integration.common.configuration;

import java.util.HashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties
@ConfigurationProperties(prefix = "it")
public class TestConfiguration {

  @Value("${TEST_ENV:dev}")
  private String testEnv;

  private HashMap<String, String> wsmUrls;
  private HashMap<String, String> wsmEndpoints;
  private HashMap<String, String> dataRepoInstanceNames;
  private String serviceAccountEmail;
  private String serviceAccountFilePath;

  public void setWsmEndpoints(HashMap<String, String> wsmEndpoints) {
    this.wsmEndpoints = wsmEndpoints;
  }

  public void setWsmUrls(HashMap<String, String> wsmUrls) {
    this.wsmUrls = wsmUrls;
  }

  public String getWsmWorkspacesBaseUrl() {
    return this.wsmUrls.get(testEnv) + this.wsmEndpoints.get("workspaces");
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

  public String getDataRepoInstanceNameFromEnv() {
    return this.dataRepoInstanceNames.get(testEnv);
  }
}
