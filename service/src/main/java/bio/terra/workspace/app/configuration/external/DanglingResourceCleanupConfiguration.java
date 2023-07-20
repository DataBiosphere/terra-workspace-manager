package bio.terra.workspace.app.configuration.external;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties
@ConfigurationProperties(prefix = "workspace.dangling-resource-cleanup")
public class DanglingResourceCleanupConfiguration {
  /** Whether to enable resource cleanup */
  private boolean enabled;

  /** How frequently to run the cleanup loop, in minutes */
  private Duration pollingInterval;

  /** Seconds to wait after startup to begin cleanup check polling */
  private Duration startupWait;

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public Duration getPollingInterval() {
    return pollingInterval;
  }

  public void setPollingInterval(Duration pollingInterval) {
    this.pollingInterval = pollingInterval;
  }

  public Duration getStartupWait() {
    return startupWait;
  }

  public void setStartupWait(Duration startupWait) {
    this.startupWait = startupWait;
  }
}
