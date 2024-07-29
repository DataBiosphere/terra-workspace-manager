package bio.terra.workspace.app;

import bio.terra.common.db.DataSourceManager;
import bio.terra.common.migrate.LiquibaseMigrator;
import bio.terra.landingzone.library.LandingZoneMain;
import bio.terra.workspace.app.configuration.external.BufferServiceConfiguration;
import bio.terra.workspace.app.configuration.external.FeatureConfiguration;
import bio.terra.workspace.app.configuration.external.WorkspaceDatabaseConfiguration;
import bio.terra.workspace.common.utils.Rethrow;
import bio.terra.workspace.service.buffer.BufferService;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.job.StairwayInitializerService;
import bio.terra.workspace.service.policy.TpsApiDispatch;
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
    SamService samService = applicationContext.getBean(SamService.class);
    BufferService bufferService = applicationContext.getBean(BufferService.class);
    BufferServiceConfiguration bufferConfig = applicationContext.getBean(BufferServiceConfiguration.class);
    TpsApiDispatch tpsApiDispatch = applicationContext.getBean(TpsApiDispatch.class);

    // Log the state of the feature flags
    featureConfiguration.logFeatures();

    // verify buffer service configuration
    if (bufferConfig.getEnabled()) {
      bufferService.verifyConfiguration();
    }

    // verify policy service configuration
    if (featureConfiguration.isTpsEnabled()) {
      tpsApiDispatch.verifyConfiguration();
    }

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

    // Initialize the WSM application service
    Rethrow.onInterrupted(
        samService::initializeWsmServiceAccount, "Initialize WSM service account");

    // Process the WSM application configuration
    appService.configure();

    // Initialize Terra Landing Zone library
    LandingZoneMain.initialize(applicationContext, migrateService);
  }
}
