package bio.terra.workspace.integration.common.configuration;

import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties
@ConfigurationProperties(prefix = "workspace.integration-test")
public class IntegrationTestConfiguration {

  @Value("${TEST_ENV:local}")
  private String testEnv;

  private Map<String, String> wsmUrls;
  private Map<String, String> wsmEndpoints;
  private Map<String, String> dataRepoInstanceNames;
  private Map<String, String> dataRepoSnapshotId;
  /** What user to impersonate to run the integration tests. */
  private Map<String, String> userEmails;
  /**
   * The path to the service account to use. This service account should be delegated to impersonate
   * users. https://developers.google.com/admin-sdk/directory/v1/guides/delegation
   */
  private String userDelegatedServiceAccountPath;

  public void setWsmEndpoints(Map<String, String> wsmEndpoints) {
    this.wsmEndpoints = wsmEndpoints;
  }

  public void setWsmUrls(Map<String, String> wsmUrls) {
    this.wsmUrls = wsmUrls;
  }

  public String getWsmWorkspacesBaseUrl() {
    return this.wsmUrls.get(testEnv) + this.wsmEndpoints.get("workspaces");
  }

  public String getUserEmail() {
    return userEmails.get(testEnv);
  }

  public void setUserEmails(Map<String, String> userEmails) {
    this.userEmails = userEmails;
  }

  public String getUserDelegatedServiceAccountPath() {
    return userDelegatedServiceAccountPath;
  }

  public void setUserDelegatedServiceAccountPath(String userDelegatedServiceAccountPath) {
    this.userDelegatedServiceAccountPath = userDelegatedServiceAccountPath;
  }

  public String getDataRepoInstanceNameFromEnv() {
    return this.dataRepoInstanceNames.get(testEnv);
  }

  public void setDataRepoInstanceNames(HashMap<String, String> instanceNames) {
    this.dataRepoInstanceNames = instanceNames;
  }

  public String getDataRepoSnapshotIdFromEnv() {
    return this.dataRepoSnapshotId.get(testEnv);
  }

  public void setDataRepoSnapshotId(HashMap<String, String> snapshotIds) {
    this.dataRepoSnapshotId = snapshotIds;
  }
}
