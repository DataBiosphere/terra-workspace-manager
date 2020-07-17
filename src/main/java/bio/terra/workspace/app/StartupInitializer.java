package bio.terra.workspace.app;

import bio.terra.workspace.app.configuration.WorkspaceManagerJdbcConfiguration;
import bio.terra.workspace.service.job.JobService;
import bio.terra.workspace.service.migrate.MigrateService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;

public final class StartupInitializer {
  private static final Logger logger = LoggerFactory.getLogger(StartupInitializer.class);
  private static final String changelogPath = "db/changelog.xml";

  public static void initialize(ApplicationContext applicationContext) {
    // Initialize or upgrade the database depending on the configuration
    MigrateService migrateService = (MigrateService) applicationContext.getBean("migrateService");
    WorkspaceManagerJdbcConfiguration workspaceManagerJdbcConfiguration =
        (WorkspaceManagerJdbcConfiguration)
            applicationContext.getBean("workspaceManagerJdbcConfiguration");
    JobService jobService = (JobService) applicationContext.getBean("jobService");

    if (workspaceManagerJdbcConfiguration.isInitializeOnStart()) {
      migrateService.initialize(changelogPath, workspaceManagerJdbcConfiguration.getDataSource());
    } else if (workspaceManagerJdbcConfiguration.isUpgradeOnStart()) {
      migrateService.upgrade(changelogPath, workspaceManagerJdbcConfiguration.getDataSource());
    }

    // The JobService initialization also handles Stairway initialization.
    jobService.initialize();

    // TODO: Fill in this method with any other initialization that needs to happen
    //  between the point of having the entire application initialized and
    //  the point of opening the port to start accepting REST requests.

  }
}
