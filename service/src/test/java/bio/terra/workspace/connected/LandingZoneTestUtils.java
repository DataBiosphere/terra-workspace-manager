package bio.terra.workspace.connected;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("connected-test")
public class LandingZoneTestUtils {
  @Value("${workspace.azure-test.default-landing-zone-id}")
  private String defaultLandingZoneId;

  public String getDefaultLandingZoneId() {
    return defaultLandingZoneId;
  }
}
