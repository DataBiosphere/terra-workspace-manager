package bio.terra.workspace.app.configuration.spring;

import bio.terra.common.kubernetes.KubeProperties;
import bio.terra.common.kubernetes.KubeService;
import bio.terra.common.stairway.StairwayComponent;
import bio.terra.common.stairway.StairwayProperties;
import bio.terra.landingzone.library.configuration.LandingZoneAzureConfiguration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class LandingZoneStairwayConfiguration {

  @Bean("landingZoneStairwayProperties")
  @ConfigurationProperties(prefix = "landingzone.stairway")
  public StairwayProperties getStairwayProperties() {
    return new StairwayProperties();
  }

  @Bean("landingZoneStairwayComponent")
  public StairwayComponent getStairwayComponent(
      KubeService kubeService,
      KubeProperties kubeProperties,
      @Qualifier("landingZoneStairwayProperties") StairwayProperties stairwayProperties) {
    return new StairwayComponent(kubeService, kubeProperties, stairwayProperties);
  }

  @Bean("landingZoneAzureConfiguration")
  @ConfigurationProperties(prefix = "workspace.azure")
  public LandingZoneAzureConfiguration getAzureConfiguration(
      bio.terra.workspace.app.configuration.external.AzureConfiguration azureConfiguration) {
    var configuration = new LandingZoneAzureConfiguration();
    configuration.setManagedAppClientId(azureConfiguration.getManagedAppClientId());
    configuration.setManagedAppClientSecret(azureConfiguration.getManagedAppClientSecret());
    configuration.setManagedAppTenantId(azureConfiguration.getManagedAppTenantId());
    return configuration;
  }
}
