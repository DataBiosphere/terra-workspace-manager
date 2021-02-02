package bio.terra.workspace.app.configuration.external;

import org.jetbrains.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties
@ConfigurationProperties(prefix = "workspace.tracing")
public class TracingConfiguration {
  /** Rate of sampling, 0.0 - 1.0 */
  @Nullable Double probability = null;
  /** GCP project id for trace */
  @Nullable String projectId = null;

  public @Nullable Double getProbability() {
    return probability;
  }

  public void setProbability(Double probability) {
    this.probability = probability;
  }

  public @Nullable String getProjectId() {
    return projectId;
  }

  public void setProjectId(String projectId) {
    this.projectId = projectId;
  }
}
