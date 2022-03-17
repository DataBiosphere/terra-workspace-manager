package bio.terra.workspace.app.configuration.external;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties
@ConfigurationProperties(prefix = "workspace.cli")
public class CliConfiguration {

  private String oldestSupportedVersion;

  private String serverName;

  public String getOldestSupportedVersion() {
    return oldestSupportedVersion;
  }

  public void setOldestSupportedVersion(String oldestSupportedVersion) {
    this.oldestSupportedVersion = oldestSupportedVersion;
  }

  public String getServerName() {
    return serverName;
  }

  public void setServerName(String serverName) {
    this.serverName = serverName;
  }
}
