package bio.terra.workspace.service.features;

import bio.terra.workspace.common.exception.FeatureNotSupportedException;
import javax.annotation.Nullable;
import org.springframework.stereotype.Component;

/**
 * TODO: fully remove FeatureService. AWS features are always disabled.
 * https://broadworkbench.atlassian.net/browse/WOR-1559
 */
@Component
public class FeatureService {
  // list of features
  public static final String AWS_ENABLED = "vwb__aws_enabled";
  public static final String AWS_APPLICATIONS_ENABLED = "vwb__aws_applications_enabled";

  public boolean isFeatureEnabled(String featureName) {
    return isFeatureEnabled(featureName, /* userEmail= */ null);
  }

  public boolean isFeatureEnabled(String featureName, @Nullable String userEmail) {
    return false;
  }

  public void featureEnabledCheck(String featureName) {
    featureEnabledCheck(featureName, /* userEmail= */ null);
  }

  public void featureEnabledCheck(String featureName, @Nullable String userEmail) {
    if (!isFeatureEnabled(featureName, userEmail)) {
      throw new FeatureNotSupportedException(
          String.format("Feature %s not supported.", featureName));
    }
  }
}
