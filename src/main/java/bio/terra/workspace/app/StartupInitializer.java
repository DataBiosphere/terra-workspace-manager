package bio.terra.workspace.app;

import bio.terra.stairway.exception.DatabaseOperationException;
import bio.terra.stairway.exception.DatabaseSetupException;
import bio.terra.stairway.exception.MigrateException;
import bio.terra.workspace.app.configuration.ApplicationConfiguration;
import bio.terra.workspace.app.configuration.StairwayJdbcConfiguration;
import bio.terra.workspace.app.configuration.WorkspaceManagerJdbcConfiguration;
import bio.terra.workspace.common.exception.stairway.StairwayInitializationException;
import bio.terra.workspace.service.migrate.MigrateService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;

public final class StartupInitializer {
  private static final Logger logger = LoggerFactory.getLogger(StartupInitializer.class);
  private static final String changelogPath = "db/changelog.xml";

  public static void initialize(ApplicationContext applicationContext) {
    // Initialize or upgrade the database depending on the configuration
    ApplicationConfiguration appConfig =
        (ApplicationConfiguration) applicationContext.getBean("applicationConfiguration");
    MigrateService migrateService = (MigrateService) applicationContext.getBean("migrateService");
    WorkspaceManagerJdbcConfiguration workspaceManagerJdbcConfiguration =
        (WorkspaceManagerJdbcConfiguration)
            applicationContext.getBean("workspaceManagerJdbcConfiguration");
    StairwayJdbcConfiguration stairwayJdbcConfiguration =
        (StairwayJdbcConfiguration) applicationContext.getBean("stairwayJdbcConfiguration");

    if (workspaceManagerJdbcConfiguration.isInitializeOnStart()) {
      migrateService.initialize(changelogPath, workspaceManagerJdbcConfiguration.getDataSource());
    } else if (workspaceManagerJdbcConfiguration.isUpgradeOnStart()) {
      migrateService.upgrade(changelogPath, workspaceManagerJdbcConfiguration.getDataSource());
    }

    // TODO: TEMPLATE: Fill in this method with any other initialization that needs to happen
    //  between the point of having the entire application initialized and
    //  the point of opening the port to start accepting REST requests.
    try {
      appConfig
          .getStairway()
          .initialize(
              stairwayJdbcConfiguration.getDataSource(),
              stairwayJdbcConfiguration.isForceClean(),
              stairwayJdbcConfiguration.isMigrateUpgrade());
    } catch (DatabaseSetupException | DatabaseOperationException | MigrateException e) {
      throw new StairwayInitializationException(
          "Stairway failed during initialization: " + e.toString());
    }
  }
}
