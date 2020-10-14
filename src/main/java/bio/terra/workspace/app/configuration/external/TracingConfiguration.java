package bio.terra.workspace.app.configuration.external;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties
@ConfigurationProperties(prefix = "workspace.tracing")
public class TracingConfiguration {
  /** Rate of sampling, 0.0 - 1.0 */
  Float probability = null;
  /** GCP project id for trace */
  String projectId = null;
  /** Path to SA json credentials for uploading traces to GCP */
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
