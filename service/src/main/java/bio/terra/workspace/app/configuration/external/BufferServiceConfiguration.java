package bio.terra.workspace.app.configuration.external;

import bio.terra.common.exception.InternalServerErrorException;
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.common.collect.ImmutableList;
import java.io.FileInputStream;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Configuration for managing connection to Buffer Service. * */
@Configuration
@EnableConfigurationProperties
@ConfigurationProperties(prefix = "workspace.buffer")
public class BufferServiceConfiguration {

  // TODO(PF-302): Clean up once fully using Buffer Service in all environments.
  private boolean enabled = false;
  private String instanceUrl;
  private String poolId;
  private String clientCredentialFilePath;

  private final FeatureConfiguration features;

  private static final ImmutableList<String> BUFFER_SCOPES =
      ImmutableList.of("openid", "email", "profile");

  @Autowired
  public BufferServiceConfiguration(FeatureConfiguration features) {
    this.features = features;
  }

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

  public String getClientCredentialFilePath() {
    return clientCredentialFilePath;
  }

  public String getAccessToken() {
    try {
      if (features.isAzureControlPlaneEnabled()) {
        throw new InternalServerErrorException(
            "BufferService is not compatible with azure control plane enabled.");
      } else {
        FileInputStream fileInputStream = new FileInputStream(clientCredentialFilePath);
        GoogleCredentials credentials =
            ServiceAccountCredentials.fromStream(fileInputStream).createScoped(BUFFER_SCOPES);
        AccessToken token = credentials.refreshAccessToken();
        return token.getTokenValue();
      }
    } catch (IOException e) {
      throw new InternalServerErrorException("Internal server error retrieving WSM credentials", e);
    }
  }
}
