package bio.terra.workspace.integration.common.configuration;

import java.util.HashMap;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties
@ConfigurationProperties(prefix = "workspace.integration-test")
public class IntegrationTestConfiguration {

  private String testEnv;
  private HashMap<String, String> wsmUrls;
  private HashMap<String, String> wsmEndpoints;
  private HashMap<String, String> dataRepoInstanceNames;
  private HashMap<String, String> dataRepoSnapshotId;
  /** What user to impersonate to run the integration tests. */
  private String userEmail;
  /**
   * The path to the service account to use. This service account should be delegated to impersonate
   * users. https://developers.google.com/admin-sdk/directory/v1/guides/delegation
   */
  private String userDelegatedServiceAccountPath;

  public void setTestEnv(String testEnv) {
    this.testEnv = testEnv;
  }

  public void setWsmEndpoints(HashMap<String, String> wsmEndpoints) {
    this.wsmEndpoints = wsmEndpoints;
  }

  public void setWsmUrls(HashMap<String, String> wsmUrls) {
    this.wsmUrls = wsmUrls;
  }

  public String getWsmWorkspacesBaseUrl() {
    return this.wsmUrls.get(testEnv) + this.wsmEndpoints.get("workspaces");
  }

  public String getUserEmail() {
    return userEmail;
  }

  public void setUserEmail(String userEmail) {
    this.userEmail = userEmail;
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
