package bio.terra.workspace.app;

import bio.terra.common.db.DataSourceInitializer;
import bio.terra.common.migrate.LiquibaseMigrator;
import bio.terra.policy.library.TpsMain;
import bio.terra.workspace.app.configuration.external.FeatureConfiguration;
import bio.terra.workspace.app.configuration.external.WorkspaceDatabaseConfiguration;
import bio.terra.workspace.service.job.JobService;
import bio.terra.workspace.service.workspace.WsmApplicationService;
import javax.sql.DataSource;
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
    FeatureConfiguration featureConfiguration =
        applicationContext.getBean(FeatureConfiguration.class);

    // Log the state of the feature flags
    featureConfiguration.logFeatures();

    // Migrate the database
    DataSource workspaceDataSource =
        DataSourceInitializer.initializeDataSource(workspaceDatabaseConfiguration);
    if (workspaceDatabaseConfiguration.isInitializeOnStart()) {
      migrateService.initialize(changelogPath, workspaceDataSource);
    } else if (workspaceDatabaseConfiguration.isUpgradeOnStart()) {
      migrateService.upgrade(changelogPath, workspaceDataSource);
    }

    // The JobService initialization also handles Stairway initialization.
    jobService.initialize();

    // Process the WSM application configuration
    appService.configure();

    // Initialize Terra Policy Service library
    TpsMain.initialize(applicationContext, migrateService);

    // NOTE:
    // Fill in this method with any other initialization that needs to happen
    // between the point of having the entire application initialized and
    // the point of opening the port to start accepting REST requests.
  }
}
