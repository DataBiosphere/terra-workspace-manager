package bio.terra.workspace.app.configuration.external;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties
@ConfigurationProperties(prefix = "workspace.sam")
public class SamConfiguration {
  /** URL of the SAM instance */
  private String basePath;

  private Duration permissionsCacheTtl;

  public String getBasePath() {
    return basePath;
  }

  public void setBasePath(String basePath) {
    this.basePath = basePath;
  }

  public Duration getPermissionsCacheTtl() {
    return permissionsCacheTtl;
  }

  public void setPermissionsCacheTtl(Duration permissionsCacheTtl) {
    this.permissionsCacheTtl = permissionsCacheTtl;
  }
}
