package bio.terra.workspace.app.configuration.external;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "workspace.startup")
public class StartupConfiguration {
  private boolean exitAfterInitialization;

  public boolean isExitAfterInitialization() {
    return exitAfterInitialization;
  }

  public StartupConfiguration setExitAfterInitialization(boolean exitAfterInitialization) {
    this.exitAfterInitialization = exitAfterInitialization;
    return this;
  }
}
