package bio.terra.workspace.app;

import bio.terra.workspace.app.configuration.JdbcConfiguration;
import bio.terra.workspace.service.job.JobService;
import bio.terra.workspace.service.migrate.MigrateService;
import org.springframework.context.ApplicationContext;

public final class StartupInitializer {
  private static final String changelogPath = "db/changelog.xml";

  public static void initialize(ApplicationContext applicationContext) {
    // Initialize or upgrade the database depending on the configuration
    MigrateService migrateService = (MigrateService) applicationContext.getBean("migrateService");
    JdbcConfiguration jdbcConfiguration =
        (JdbcConfiguration) applicationContext.getBean("jdbcConfiguration");
    JobService jobService = (JobService) applicationContext.getBean("jobService");

    if (jdbcConfiguration.isInitializeOnStart()) {
      migrateService.initialize(changelogPath, jdbcConfiguration.getDataSource());
    } else if (jdbcConfiguration.isUpgradeOnStart()) {
      migrateService.upgrade(changelogPath, jdbcConfiguration.getDataSource());
    }

    // The JobService initialization also handles Stairway initialization.
    jobService.initialize();

    // TODO: Fill in this method with any other initialization that needs to happen
    //  between the point of having the entire application initialized and
    //  the point of opening the port to start accepting REST requests.

  }
}
