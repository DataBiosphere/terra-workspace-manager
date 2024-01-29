package bio.terra.workspace.app.configuration.external;

import bio.terra.common.db.BaseDatabaseProperties;
import bio.terra.common.db.DataSourceInitializer;
import javax.sql.DataSource;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@Configuration
@EnableConfigurationProperties
@EnableTransactionManagement
@ConfigurationProperties(prefix = "workspace.workspace-database")
public class WorkspaceDatabaseConfiguration extends BaseDatabaseProperties {
  // These properties control code in the StartupInitializer. We would not use these in production,
  // but they
  // are handy to set for development and testing. There are only three interesting states:
  // 1. initialize is true; upgrade is irrelevant - initialize and recreate an empty database
  // 2. initialize is false; upgrade is true - apply changesets to an existing database
  // 3. initialize is false; upgrade is false - do nothing to the database
  /** If true, primary database will be wiped */
  private boolean initializeOnStart;

  /** If true, primary database will have changesets applied */
  private boolean upgradeOnStart;

  public boolean isInitializeOnStart() {
    return initializeOnStart;
  }

  public void setInitializeOnStart(boolean initializeOnStart) {
    this.initializeOnStart = initializeOnStart;
  }

  public boolean isUpgradeOnStart() {
    return upgradeOnStart;
  }

  public void setUpgradeOnStart(boolean upgradeOnStart) {
    this.upgradeOnStart = upgradeOnStart;
  }

  // Not a property
  private DataSource dataSource;

  public DataSource getDataSource() {
    // Lazy allocation of the data source
    if (dataSource == null) {
      dataSource = DataSourceInitializer.initializeDataSource(this);
    }
    return dataSource;
  }

  // This bean plus the @EnableTransactionManagement annotation above enables the use of the
  // @Transaction annotation to control the transaction properties of the data source.
  @Bean("transactionManager")
  public PlatformTransactionManager getTransactionManager() {
    return new JdbcTransactionManager(getDataSource());
  }
}
