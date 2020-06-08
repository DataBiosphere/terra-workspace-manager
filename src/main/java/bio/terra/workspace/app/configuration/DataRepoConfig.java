package bio.terra.workspace.app.configuration;

import java.util.HashMap;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties
@ConfigurationProperties(prefix = "datarepo")
public class DataRepoConfig {
  private HashMap<String, String> instances;

  public HashMap<String, String> getInstances() {
    return instances;
  }

  public void setInstances(HashMap<String, String> instances) {
    this.instances = instances;
  }
}
