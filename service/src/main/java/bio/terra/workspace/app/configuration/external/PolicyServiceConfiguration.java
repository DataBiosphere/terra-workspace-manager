package bio.terra.workspace.app.configuration.external;

import bio.terra.common.exception.InternalServerErrorException;
import bio.terra.workspace.common.utils.AuthUtils;
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

  public String getAccessToken() {
    try {
      return AuthUtils.getAccessToken(
          features.isAzureControlPlaneEnabled(),
          POLICY_SERVICE_ACCOUNT_SCOPES,
          clientCredentialFilePath);
    } catch (IOException e) {
      throw new InternalServerErrorException("Internal server error retrieving policy service client credentials", e);
    }
  }
}
