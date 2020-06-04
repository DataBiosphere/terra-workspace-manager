package bio.terra.workspace.app.configuration;

import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties
@ConfigurationProperties(prefix = "datarepo")
public class DataRepoConfig {
  private Set<String> instances;

  public Set<String> getInstances() {
    return instances;
  }

  public void setInstances(Set<String> instances) {
    this.instances = instances;
  }
}
