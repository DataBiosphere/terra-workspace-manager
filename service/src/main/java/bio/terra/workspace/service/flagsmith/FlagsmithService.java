package bio.terra.workspace.service.flagsmith;

import bio.terra.workspace.app.configuration.external.FeatureConfiguration;
import com.fasterxml.jackson.databind.node.TextNode;
import com.flagsmith.FlagsmithClient;
import com.flagsmith.config.FlagsmithCacheConfig;
import com.flagsmith.config.FlagsmithConfig;
import com.flagsmith.exceptions.FlagsmithApiError;
import com.flagsmith.exceptions.FlagsmithClientError;
import com.flagsmith.models.Flags;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class FlagsmithService {

  private static final Logger logger = LoggerFactory.getLogger(FlagsmithService.class);
  private final FlagsmithClient flagsmith;
  public FlagsmithService(FeatureConfiguration featureConfiguration) {
    this.flagsmith = FlagsmithClient.newBuilder()
          .withConfiguration(
              FlagsmithConfig.newBuilder()
                  .baseUri(featureConfiguration.getFlagsmithApiPath()).build()
          )
          .withCache(FlagsmithCacheConfig.newBuilder().build())
          .setApiKey(featureConfiguration.getFlagsmithApiKey()).build();
  }

  public boolean isFeatureEnabled(String featureName) {
    try {
      return getFlags().isFeatureEnabled(featureName);
    } catch (FlagsmithClientError e) {
      logger.warn(String.format("Fail to get the state of flag %s", featureName), e);
      return false;
    }
  }

  public String getStringValue(String featureName) {
    var value = getFeatureValue(featureName);
    if (value == null) {
      return null;
    }
    String stringValue = value instanceof String ? (String) value : ((TextNode) value).textValue();
    return stringValue;
  }

  public Boolean getBoolValue(String featureName) {
    var value = getFeatureValue(featureName);
    if (value == null) {
      return null;
    }
    return (Boolean) value;
  }

  private Object getFeatureValue(String featureName) {
    try {
      return getFlags().getFeatureValue(featureName);
    } catch (FlagsmithClientError e) {
      logger.warn(String.format("Fail to get the state of flag %s", featureName), e);
      return null;
    }
  }

  private Flags getFlags() {
    if (flagsmith == null) {
      logger.warn("Flagsmith is not available");
      return null;
    }
    try {
      return flagsmith.getEnvironmentFlags();
    } catch (FlagsmithApiError e) {
      logger.warn("Failed to get environment flags", e);
      return null;
    }
  }
}
