package bio.terra.workspace.app;

import bio.terra.workspace.app.configuration.WorkspaceJdbcConfiguration;
import bio.terra.workspace.service.job.JobService;
import bio.terra.workspace.service.migrate.MigrateService;
import org.springframework.context.ApplicationContext;

public final class StartupInitializer {
  private static final String changelogPath = "db/changelog.xml";

  public static void initialize(ApplicationContext applicationContext) {
    // Initialize or upgrade the database depending on the configuration
    MigrateService migrateService = (MigrateService) applicationContext.getBean("migrateService");
    WorkspaceJdbcConfiguration workspaceJdbcConfiguration =
        (WorkspaceJdbcConfiguration) applicationContext.getBean("workspaceJdbcConfiguration");
    JobService jobService = (JobService) applicationContext.getBean("jobService");

    if (workspaceJdbcConfiguration.isInitializeOnStart()) {
      migrateService.initialize(changelogPath, workspaceJdbcConfiguration.getDataSource());
    } else if (workspaceJdbcConfiguration.isUpgradeOnStart()) {
      migrateService.upgrade(changelogPath, workspaceJdbcConfiguration.getDataSource());
    }

    // The JobService initialization also handles Stairway initialization.
    jobService.initialize();

    // TODO: Fill in this method with any other initialization that needs to happen
    //  between the point of having the entire application initialized and
    //  the point of opening the port to start accepting REST requests.

  }
}
