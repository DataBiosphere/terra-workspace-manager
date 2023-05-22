package bio.terra.workspace.app.configuration.external;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@Component
@EnableConfigurationProperties
@EnableTransactionManagement
@ConfigurationProperties(prefix = "workspace.notifications")
public class NotificationsConfig {

  private String credentialsFilePath;
  private String pubsubProjectName;
  private String pubsubTopic;

  public String getCredentialsFilePath() {
    return credentialsFilePath;
  }

  public void setCredentialsFilePath(String credentialsFilePath) {
    this.credentialsFilePath = credentialsFilePath;
  }

  public String getPubsubProjectName() {
    return pubsubProjectName;
  }

  public void setPubsubProjectName(String pubsubProjectName) {
    this.pubsubProjectName = pubsubProjectName;
  }

  public String getPubsubTopic() {
    return pubsubTopic;
  }

  public void setPubsubTopic(String pubsubTopic) {
    this.pubsubTopic = pubsubTopic;
  }
}
