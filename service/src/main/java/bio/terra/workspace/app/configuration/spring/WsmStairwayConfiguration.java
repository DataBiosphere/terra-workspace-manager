package bio.terra.workspace.app.configuration.spring;

import bio.terra.common.kubernetes.KubeProperties;
import bio.terra.common.kubernetes.KubeService;
import bio.terra.common.stairway.StairwayComponent;
import bio.terra.common.stairway.StairwayProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class WsmStairwayConfiguration {
  @Bean("wsmStairwayProperties")
  @Primary
  @ConfigurationProperties(prefix = "terra.common.stairway")
  public StairwayProperties getStairwayProperties() {
    return new StairwayProperties();
  }

  @Bean("wsmStairwayComponent")
  @Primary
  public StairwayComponent getStairwayComponent(
      KubeService kubeService,
      KubeProperties kubeProperties,
      @Qualifier("wsmStairwayProperties") StairwayProperties stairwayProperties) {
    return new StairwayComponent(kubeService, kubeProperties, stairwayProperties);
  }
}
