package bio.terra.workspace.app.configuration.external;

import bio.terra.common.exception.InternalServerErrorException;
import com.azure.core.credential.AccessToken;
import com.azure.core.credential.TokenCredential;
import com.azure.core.credential.TokenRequestContext;
import com.azure.identity.DefaultAzureCredentialBuilder;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Configuration for managing connection to Terra Policy Service. * */
@Configuration
@EnableConfigurationProperties
@ConfigurationProperties(prefix = "workspace.policy")
public class PolicyServiceConfiguration {

  private String basePath;
  private String clientCredentialFilePath;

  private static final ImmutableList<String> POLICY_SERVICE_ACCOUNT_SCOPES =
      ImmutableList.of("openid", "email", "profile");

  private final FeatureConfiguration features;

  @Autowired
  public PolicyServiceConfiguration(FeatureConfiguration features) {
    this.features = features;
  }

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
    try {
      if (features.isAzureControlPlaneEnabled()) {
        TokenCredential credential = new DefaultAzureCredentialBuilder().build();
        // The Microsoft Authentication Library (MSAL) currently specifies offline_access, openid,
        // profile, and email by default in authorization and token requests.
        AccessToken token =
            credential
                .getToken(
                    new TokenRequestContext().addScopes("https://graph.microsoft.com/.default"))
                .block();
        return token.getToken();
      } else {
        GoogleCredentials creds =
            GoogleCredentials.getApplicationDefault().createScoped(POLICY_SERVICE_ACCOUNT_SCOPES);
        creds.refreshIfExpired();
        return creds.getAccessToken().getTokenValue();
      }
    } catch (IOException e) {
      throw new InternalServerErrorException("Internal server error retrieving WSM credentials", e);
    }
  }
}
