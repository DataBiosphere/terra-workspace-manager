package bio.terra.workspace.amalgam.landingzone.azure;

import bio.terra.landingzone.service.landingzone.azure.LandingZoneService;
import bio.terra.workspace.app.configuration.external.FeatureConfiguration;
import bio.terra.workspace.app.configuration.external.LandingZoneServiceConfiguration;
import io.opentelemetry.api.OpenTelemetry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class LandingZoneServiceFactory {

  private final LandingZoneService amalgamatedLandingZoneService;
  private final LandingZoneServiceConfiguration lzsConfiguration;
  private final FeatureConfiguration featureConfiguration;
  private final OpenTelemetry openTelemetry;

  @Autowired
  public LandingZoneServiceFactory(
      FeatureConfiguration featureConfiguration,
      OpenTelemetry openTelemetry,
      LandingZoneService landingZoneService,
      LandingZoneServiceConfiguration lzsConfiguration) {
    this.featureConfiguration = featureConfiguration;
    this.openTelemetry = openTelemetry;
    this.amalgamatedLandingZoneService = landingZoneService;
    this.lzsConfiguration = lzsConfiguration;
  }

  public WorkspaceLandingZoneService getLandingZoneService() {
    if (this.featureConfiguration.isLzsEnabled()) {
      return new HttpLandingZoneService(openTelemetry, this.lzsConfiguration);
    } else {
      return new AmalgamatedLandingZoneService(amalgamatedLandingZoneService);
    }
  }
}
