package bio.terra.workspace.app.configuration.external;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties
@ConfigurationProperties(prefix = "feature")
public class FeatureConfiguration {
  private static final Logger logger = LoggerFactory.getLogger(FeatureConfiguration.class);

  private boolean azureEnabled;

  public boolean isAzureEnabled() {
    return azureEnabled;
  }

  public void setAzureEnabled(boolean azureEnabled) {
    this.azureEnabled = azureEnabled;
  }

  /**
   * Write the feature settings into the log
   * Add an entry here for each new feature
   */
  public void logFeatures() {
    logger.info("Feature: azure-enabled: {}", isAzureEnabled());
  }

}
