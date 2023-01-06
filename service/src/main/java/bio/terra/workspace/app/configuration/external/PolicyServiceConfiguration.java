package bio.terra.workspace.app.configuration.external;

import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.common.collect.ImmutableList;
import java.io.FileInputStream;
import java.io.IOException;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Configuration;

/** Configuration for managing connection to Terra Policy Service. * */
@Configuration
@EnableConfigurationProperties
@ConfigurationProperties(prefix = "workspace.policy")
@EnableCaching
public class PolicyServiceConfiguration {

  private String basePath;
  private String clientCredentialFilePath;

  private static final ImmutableList<String> POLICY_SERVICE_ACCOUNT_SCOPES =
      ImmutableList.of("openid", "email", "profile");

  public String getBasePath() {
    return basePath;
  }

  public void setBasePath(String basePath) {
    this.basePath = basePath;
  }

  public void setClientCredentialFilePath(String clientCredentialFilePath) {
    this.clientCredentialFilePath = clientCredentialFilePath;
  }

  public String getClientCredentialFilePath() {
    return clientCredentialFilePath;
  }

  public String getAccessToken() throws IOException {
    try (FileInputStream fileInputStream = new FileInputStream(clientCredentialFilePath)) {
      GoogleCredentials credentials =
          ServiceAccountCredentials.fromStream(fileInputStream)
              .createScoped(POLICY_SERVICE_ACCOUNT_SCOPES);
      AccessToken token = credentials.refreshAccessToken();
      return token.getTokenValue();
    }
  }
}
