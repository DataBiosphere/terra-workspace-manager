package bio.terra.workspace.service.features;

import bio.terra.common.flagsmith.FlagsmithService;
import bio.terra.workspace.common.exception.FeatureNotSupportedException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class FeatureService {
  private final FlagsmithService flagsmithService;

  @Autowired
  FeatureService(FlagsmithService flagsmithService) {
    this.flagsmithService = flagsmithService;
  }

  public boolean awsEnabled() {
    return flagsmithService.isFeatureEnabled("terra__aws_enabled").orElse(false);
  }

  public void awsEnabledCheck() {
    if (!awsEnabled()) {
      throw new FeatureNotSupportedException("AWS feature are not enabled");
    }
  }

  public boolean stackdriverExporterEnabled() {
    return flagsmithService
        .isFeatureEnabled("terra__wsm_stackdriver_exporter_enabled")
        .orElse(false);
  }
}
