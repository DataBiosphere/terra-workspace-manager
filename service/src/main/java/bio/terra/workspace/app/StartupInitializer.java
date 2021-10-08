package bio.terra.workspace.app;

import bio.terra.common.migrate.LiquibaseMigrator;
import bio.terra.workspace.app.configuration.external.WorkspaceDatabaseConfiguration;
import bio.terra.workspace.service.job.JobService;
import bio.terra.workspace.service.workspace.WsmApplicationService;
import org.springframework.context.ApplicationContext;

public final class StartupInitializer {
  private static final String changelogPath = "db/changelog.xml";

  public static void initialize(ApplicationContext applicationContext) {
    // Initialize or upgrade the database depending on the configuration
    LiquibaseMigrator migrateService = applicationContext.getBean(LiquibaseMigrator.class);
    WorkspaceDatabaseConfiguration workspaceDatabaseConfiguration =
        applicationContext.getBean(WorkspaceDatabaseConfiguration.class);
    JobService jobService = applicationContext.getBean(JobService.class);
    WsmApplicationService appService = applicationContext.getBean(WsmApplicationService.class);

    if (workspaceDatabaseConfiguration.isInitializeOnStart()) {
      migrateService.initialize(changelogPath, workspaceDatabaseConfiguration.getDataSource());
    } else if (workspaceDatabaseConfiguration.isUpgradeOnStart()) {
      migrateService.upgrade(changelogPath, workspaceDatabaseConfiguration.getDataSource());
    }

    // The JobService initialization also handles Stairway initialization.
    jobService.initialize();

    // Process the WSM application configuration
    appService.configure();

    // TODO: Fill in this method with any other initialization that needs to happen
    //  between the point of having the entire application initialized and
    //  the point of opening the port to start accepting REST requests.

  }
}
