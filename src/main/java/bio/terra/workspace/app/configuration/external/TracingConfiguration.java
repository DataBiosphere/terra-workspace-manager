package bio.terra.workspace.app.configuration.external;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties
@ConfigurationProperties(prefix = "workspace.tracing")
public class TracingConfiguration {

  Float probability = null;
  String projectId = null;
  String saPath = null;

  public Float getProbability() {
    return probability;
  }

  public void setProbability(Float probability) {
    this.probability = probability;
  }

  public String getProjectId() {
    return projectId;
  }

  public void setProjectId(String projectId) {
    this.projectId = projectId;
  }

  public String getSaPath() {
    return saPath;
  }

  public void setSaPath(String saPath) {
    this.saPath = saPath;
  }
}
