package bio.terra.workspace.app;

import bio.terra.common.db.DataSourceManager;
import bio.terra.common.migrate.LiquibaseMigrator;
import bio.terra.landingzone.library.LandingZoneMain;
import bio.terra.workspace.app.configuration.external.FeatureConfiguration;
import bio.terra.workspace.app.configuration.external.WorkspaceDatabaseConfiguration;
import bio.terra.workspace.service.job.StairwayInitializerService;
import bio.terra.workspace.service.workspace.WsmApplicationService;
import javax.sql.DataSource;
import org.springframework.context.ApplicationContext;

public final class StartupInitializer {
  private static final String changelogPath = "db/changelog.xml";

  public static void initialize(ApplicationContext applicationContext) {
    // Initialize or upgrade the database depending on the configuration
    DataSourceManager dataSourceManager = applicationContext.getBean(DataSourceManager.class);
    WorkspaceDatabaseConfiguration workspaceDatabaseConfiguration =
        applicationContext.getBean(WorkspaceDatabaseConfiguration.class);
    LiquibaseMigrator migrateService = applicationContext.getBean(LiquibaseMigrator.class);
    StairwayInitializerService stairwayInitializerService =
        applicationContext.getBean(StairwayInitializerService.class);
    WsmApplicationService appService = applicationContext.getBean(WsmApplicationService.class);
    FeatureConfiguration featureConfiguration =
        applicationContext.getBean(FeatureConfiguration.class);

    // Log the state of the feature flags
    featureConfiguration.logFeatures();

    // Migrate the database
    DataSource workspaceDataSource =
        dataSourceManager.initializeDataSource(workspaceDatabaseConfiguration);
    if (workspaceDatabaseConfiguration.isInitializeOnStart()) {
      migrateService.initialize(changelogPath, workspaceDataSource);
    } else if (workspaceDatabaseConfiguration.isUpgradeOnStart()) {
      migrateService.upgrade(changelogPath, workspaceDataSource);
    }

    // Initialize Stairway
    stairwayInitializerService.initialize();

    // Process the WSM application configuration
    appService.configure();

    // Initialize Terra Landing Zone library
    LandingZoneMain.initialize(applicationContext, migrateService);
  }
}
