package bio.terra.workspace.integration.common.configuration;

import java.util.HashMap;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties
@ConfigurationProperties(prefix = "workspace.integration-test")
public class IntegrationTestConfiguration {

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

  // The defaulting behavior in the @Value was not consistently working. There is some
  // condition under which it was returning "null" instead of null.
  // This replacement code directly retrieves the envvar and
  // checks for both conditions. Not going to chase this, since
  // this form of integration testing is going away.
  private String computeTestEnv() {
    String testEnv = System.getenv("TEST_ENV");
    if (StringUtils.isEmpty(testEnv) || StringUtils.equals(testEnv, "null")) {
      return "local";
    }
    return testEnv;
  }

  public void setWsmEndpoints(Map<String, String> wsmEndpoints) {
    this.wsmEndpoints = wsmEndpoints;
  }

  public void setWsmUrls(Map<String, String> wsmUrls) {
    this.wsmUrls = wsmUrls;
  }

  public String getWsmWorkspacesBaseUrl() {
    return this.wsmUrls.get(computeTestEnv()) + this.wsmEndpoints.get("workspaces");
  }

  public String getUserEmail() {
    return userEmails.get(computeTestEnv());
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
    return this.dataRepoInstanceNames.get(computeTestEnv());
  }

  public void setDataRepoInstanceNames(HashMap<String, String> instanceNames) {
    this.dataRepoInstanceNames = instanceNames;
  }

  public String getDataRepoSnapshotIdFromEnv() {
    return this.dataRepoSnapshotId.get(computeTestEnv());
  }

  public void setDataRepoSnapshotId(HashMap<String, String> snapshotIds) {
    this.dataRepoSnapshotId = snapshotIds;
  }
}
