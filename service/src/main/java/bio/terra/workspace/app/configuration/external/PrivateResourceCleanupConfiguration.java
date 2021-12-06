package bio.terra.workspace.app.configuration.external;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties
@ConfigurationProperties(prefix = "workspace.private-resource-cleanup")
public class PrivateResourceCleanupConfiguration {
  /** Whether to enable private resource cleanup */
  private boolean enabled;

  /** How frequently to run the private resource cleanup loop, in minutes */
  private int pollingIntervalMinutes;

  /** Seconds to wait after startup to begin status check polling */
  private int startupWaitSeconds;

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public int getPollingIntervalMinutes() {
    return pollingIntervalMinutes;
  }

  public void setPollingIntervalMinutes(int pollingIntervalSeconds) {
    this.pollingIntervalMinutes = pollingIntervalSeconds;
  }

  public int getStartupWaitSeconds() {
    return startupWaitSeconds;
  }

  public void setStartupWaitSeconds(int startupWaitSeconds) {
    this.startupWaitSeconds = startupWaitSeconds;
  }
}
