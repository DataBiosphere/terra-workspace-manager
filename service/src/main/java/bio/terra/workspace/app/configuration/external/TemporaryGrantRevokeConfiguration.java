package bio.terra.workspace.app.configuration.external;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties
@ConfigurationProperties(prefix = "workspace.temporary-grant-revoke")
public class TemporaryGrantRevokeConfiguration {
  /** Whether to enable the revoke background process */
  private boolean revokeEnabled;

  /** How frequently to run the revoke process */
  private Duration pollingInterval;

  /** How long to wait after startup to begin revoke polling */
  private Duration startupWait;

  /** How long to hold a temporary grant before revoking */
  private Duration grantHoldTime;

  public boolean isRevokeEnabled() {
    return revokeEnabled;
  }

  public void setRevokeEnabled(boolean revokeEnabled) {
    this.revokeEnabled = revokeEnabled;
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

  public Duration getGrantHoldTime() {
    return grantHoldTime;
  }

  public void setGrantHoldTime(Duration grantHoldTime) {
    this.grantHoldTime = grantHoldTime;
  }
}
