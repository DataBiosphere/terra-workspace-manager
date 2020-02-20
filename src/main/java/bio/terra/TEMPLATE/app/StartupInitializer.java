package bio.terra.TEMPLATE.app;

import bio.terra.TEMPLATE.app.configuration.ApplicationConfiguration;
import bio.terra.TEMPLATE.service.migrate.MigrateService;
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

        if (appConfig.isDbInitializeOnStart()) {
            migrateService.initialize(changelogPath, appConfig.getDataSource());
        } else if (appConfig.isDbUpgradeOnStart()) {
            migrateService.upgrade(changelogPath, appConfig.getDataSource());
        }

        // TODO: TEMPLATE: Fill in this method with any other initialization that needs to happen
        //  between the point of having the entire application initialized and
        //  the point of opening the port to start accepting REST requests.
    }
}
