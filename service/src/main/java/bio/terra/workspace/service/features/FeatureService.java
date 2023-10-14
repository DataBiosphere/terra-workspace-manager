package bio.terra.workspace.service.features;

import bio.terra.common.flagsmith.FlagsmithService;
import bio.terra.workspace.common.exception.FeatureNotSupportedException;
import java.util.Optional;
import javax.annotation.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class FeatureService {
  // list of features
  public static final String AWS_ENABLED = "vwb__aws_enabled";
  public static final String AWS_APPLICATIONS_ENABLED = "vwb__aws_applications_enabled";
  public static final String WSM_STACKDRIVER_EXPORTER_ENABLED =
      "terra__wsm_stackdriver_exporter_enabled";
  private final FlagsmithService flagsmithService;

  @Autowired
  FeatureService(FlagsmithService flagsmithService) {
    this.flagsmithService = flagsmithService;
  }

  public boolean isFeatureEnabled(String featureName) {
    return isFeatureEnabled(featureName, /* userEmail= */ null);
  }

  public boolean isFeatureEnabled(String featureName, @Nullable String userEmail) {
    return flagsmithService.isFeatureEnabled(featureName, userEmail).orElse(false);
  }

  public void featureEnabledCheck(String featureName) {
    featureEnabledCheck(featureName, /* userEmail= */ null);
  }

  public void featureEnabledCheck(String featureName, @Nullable String userEmail) {
    if (!isFeatureEnabled(featureName, userEmail)) {
      throw new FeatureNotSupportedException(
          String.format("Feature %s not supported for user %s", featureName, userEmail));
    }
  }

  public <T> Optional<T> getFeatureValueJson(String feature, Class<T> clazz) {
    return flagsmithService.getFeatureValueJson(feature, clazz);
  }
}
