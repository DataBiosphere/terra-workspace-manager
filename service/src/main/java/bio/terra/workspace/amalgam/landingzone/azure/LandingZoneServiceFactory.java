package bio.terra.workspace.amalgam.landingzone.azure;

import bio.terra.landingzone.service.landingzone.azure.LandingZoneService;
import bio.terra.workspace.app.configuration.external.FeatureConfiguration;
import io.opentelemetry.api.OpenTelemetry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class LandingZoneServiceFactory {

  private final LandingZoneService amalgamatedLandingZoneService;
  private FeatureConfiguration configuration;
  private final OpenTelemetry openTelemetry;

  @Autowired
  public LandingZoneServiceFactory(
      FeatureConfiguration configuration,
      OpenTelemetry openTelemetry,
      LandingZoneService landingZoneService) {
    this.configuration = configuration;
    this.openTelemetry = openTelemetry;
    this.amalgamatedLandingZoneService = landingZoneService;
  }

  public WorkspaceLandingZoneService getLandingZoneService() {
    if (this.configuration.isLzsEnabled()) {
      return new HttpLandingZoneService(openTelemetry);
    } else {
      return new AmalgamatedLandingZoneService(amalgamatedLandingZoneService);
    }
  }
}
