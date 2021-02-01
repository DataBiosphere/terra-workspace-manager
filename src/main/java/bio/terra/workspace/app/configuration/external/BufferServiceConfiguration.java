package bio.terra.workspace.app.configuration.external;

import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.common.collect.ImmutableList;
import java.io.FileInputStream;
import java.io.IOException;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Configuration for managing connection to Buffer Service. * */
@Configuration
@EnableConfigurationProperties
@ConfigurationProperties(prefix = "workspace.buffer")
public class BufferServiceConfiguration {

  // TODO(PF-302): Clean up once fully using Buffer Service.
  private boolean enabled = false;
  private String instanceUrl;
  private String poolId;
  private String clientCredentialFilePath;

  private static final ImmutableList<String> BUFFER_SCOPES =
      ImmutableList.of("openid", "email", "profile");

  public boolean getEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public String getInstanceUrl() {
    return instanceUrl;
  }

  public void setInstanceUrl(String instanceUrl) {
    this.instanceUrl = instanceUrl;
  }

  public String getPoolId() {
    return poolId;
  }

  public void setPoolId(String poolId) {
    this.poolId = poolId;
  }

  public void setClientCredentialFilePath(String clientCredentialFilePath) {
    this.clientCredentialFilePath = clientCredentialFilePath;
  }

  public String getAccessToken() throws IOException {
    FileInputStream f = new FileInputStream(clientCredentialFilePath);
    GoogleCredentials credentials =
        ServiceAccountCredentials.fromStream(new FileInputStream(clientCredentialFilePath))
            .createScoped(BUFFER_SCOPES);
    AccessToken token = credentials.refreshAccessToken();
    return token.getTokenValue();
  }
}
