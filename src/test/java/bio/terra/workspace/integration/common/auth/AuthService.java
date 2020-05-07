package bio.terra.workspace.integration.common.auth;

import bio.terra.workspace.integration.common.configuration.TestConfiguration;
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import java.io.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class AuthService {

  private final TestConfiguration testConfig;
  private final Map<String, String> userTokens = new HashMap<>();
  private final List<String> domainWideDelegationAccessScopes =
      Arrays.asList(
          "openid",
          "email",
          "profile",
          "https://www.googleapis.com/auth/devstorage.full_control",
          "https://www.googleapis.com/auth/cloud-platform");

  @Autowired
  public AuthService(TestConfiguration testConfig) {
    this.testConfig = testConfig;
  }

  public String getAuthToken(String userEmail) throws IOException, InterruptedException {
    if (!userTokens.containsKey(userEmail)) {
      String vaultPath = testConfig.getVaultPath();
      userTokens.put(userEmail, getDomainWideDelegationAccessToken(userEmail, vaultPath));
    }
    return userTokens.get(userEmail);
  }

  private String getDomainWideDelegationAccessToken(
      String userEmail, String serviceAccountVaultPath) throws IOException, InterruptedException {
    String serviceAccountJson = getJsonSecretFromVault(serviceAccountVaultPath);
    GoogleCredentials credentials =
        GoogleCredentials.fromStream(new ByteArrayInputStream(serviceAccountJson.getBytes()))
            .createScoped(domainWideDelegationAccessScopes)
            .createDelegated(userEmail);
    credentials.refreshIfExpired();
    AccessToken newAccessToken = credentials.getAccessToken();
    return newAccessToken.getTokenValue();
  }

  private String getJsonSecretFromVault(String path) throws IOException, InterruptedException {
    String vaultAddress = testConfig.getVaultAddress();
    String vaultToken = getVaultToken();
    String command =
        "docker run --cap-add IPC_LOCK --rm -e VAULT_TOKEN="
            + vaultToken
            + " -e VAULT_ADDR="
            + vaultAddress
            + " vault:1.1.0 vault read -format json -field data "
            + path;
    return runShellCommand(command);
  }

  private String getVaultToken() throws InterruptedException, IOException {
    String userHome = System.getProperty("user.home");
    String command = "cat " + userHome + "/" + testConfig.getVaultTokenFileName();
    return runShellCommand(command);
  }

  private String runShellCommand(String command) throws IOException, InterruptedException {
    Process process = Runtime.getRuntime().exec(command);
    process.waitFor();
    BufferedReader buf = new BufferedReader(new InputStreamReader(process.getInputStream()));
    StringBuilder output = new StringBuilder();
    String line = "";
    while ((line = buf.readLine()) != null) {
      output.append(line);
    }
    return output.toString();
  }
}
