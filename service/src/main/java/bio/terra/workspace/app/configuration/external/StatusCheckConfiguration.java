package bio.terra.workspace.app.configuration.external;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties
@ConfigurationProperties(prefix = "workspace.status-check")
public class StatusCheckConfiguration {
  /** Whether to perform status checking. Allows disabling for testing. */
  private boolean enabled;

  /** Rate at which the status check is performed and the results are cached. */
  private int pollingIntervalSeconds;

  /** Seconds to wait after startup to begin status check polling */
  private int startupWaitSeconds;

  /**
   * Allowed staleness of cached status check information before the service
   * is marked as not ready. In seconds. For example, if polling interval is
   * 30 seconds, staleness might be set to 65 seconds to allow for one
   * interval to be missed before declaring not ready.
   */
  private int stalenessThresholdSeconds;

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public int getPollingIntervalSeconds() {
    return pollingIntervalSeconds;
  }

  public void setPollingIntervalSeconds(int pollingIntervalSeconds) {
    this.pollingIntervalSeconds = pollingIntervalSeconds;
  }

  public int getStartupWaitSeconds() {
    return startupWaitSeconds;
  }

  public void setStartupWaitSeconds(int startupWaitSeconds) {
    this.startupWaitSeconds = startupWaitSeconds;
  }

  public int getStalenessThresholdSeconds() {
    return stalenessThresholdSeconds;
  }

  public void setStalenessThresholdSeconds(int stalenessThresholdSeconds) {
    this.stalenessThresholdSeconds = stalenessThresholdSeconds;
  }
}
