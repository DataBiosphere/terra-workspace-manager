package bio.terra.workspace.app.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@EnableConfigurationProperties
@ConfigurationProperties(prefix = "stackdriver")
public class StackdriverConfiguration extends JdbcConfiguration {
  private String serviceAccountFilePath;
  private String projectId;
  private Double samplingProbability;

  public String getServiceAccountFilePath() {
    return serviceAccountFilePath;
  }

  public void setServiceAccountFilePath(String serviceAccountFilePath) {
    this.serviceAccountFilePath = serviceAccountFilePath;
  }

  public String getProjectId() {
    return projectId;
  }

  public void setProjectId(String projectId) {
    this.projectId = projectId;
  }

  public Double getSamplingProbability() {
    return samplingProbability;
  }

  public void setSamplingProbability(Double samplingProbability) {
    this.samplingProbability = samplingProbability;
  }

}
