package bio.terra.workspace.app.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@EnableConfigurationProperties
@ConfigurationProperties(prefix = "workspace.stairway.jdbc")
public class StairwayJdbcConfiguration extends BaseJdbcConfiguration {
  /** Passed to Stairway, true will run the migrate to upgrade the database */
  private String migrateUpgrade;
  /**
   * Passed to Stairway, true will drop any existing stairway data and purge the work queue.
   * Otherwise existing flights are recovered.
   */
  private String forceClean;

  public String getMigrateUpgrade() {
    return migrateUpgrade;
  }

  public void setMigrateUpgrade(String migrateUpgrade) {
    this.migrateUpgrade = migrateUpgrade;
  }

  public String getForceClean() {
    return forceClean;
  }

  public void setForceClean(String forceClean) {
    this.forceClean = forceClean;
  }

  public boolean isMigrateUpgrade() {
    return Boolean.parseBoolean(migrateUpgrade);
  }

  public boolean isForceClean() {
    return Boolean.parseBoolean(forceClean);
  }
}
