package bio.terra.workspace.app;

import bio.terra.common.migrate.LiquibaseMigrator;
import bio.terra.common.stairway.StairwayComponent;
import bio.terra.common.stairway.TracingHook;
import bio.terra.workspace.app.configuration.external.StairwayDatabaseConfiguration;
import bio.terra.workspace.app.configuration.external.WorkspaceDatabaseConfiguration;
import bio.terra.workspace.common.utils.FlightBeanBag;
import bio.terra.workspace.common.utils.MdcHook;
import bio.terra.workspace.service.job.JobService;
import com.google.common.collect.ImmutableList;
import javax.sql.DataSource;
import org.springframework.context.ApplicationContext;

public final class StartupInitializer {
  private static final String changelogPath = "db/changelog.xml";

  public static void initialize(ApplicationContext applicationContext) {
    // Initialize or upgrade the database depending on the configuration
    LiquibaseMigrator migrateService = applicationContext.getBean(LiquibaseMigrator.class);
    WorkspaceDatabaseConfiguration workspaceDatabaseConfiguration =
        applicationContext.getBean(WorkspaceDatabaseConfiguration.class);

    if (workspaceDatabaseConfiguration.isInitializeOnStart()) {
      migrateService.initialize(changelogPath, workspaceDatabaseConfiguration.getDataSource());
    } else if (workspaceDatabaseConfiguration.isUpgradeOnStart()) {
      migrateService.upgrade(changelogPath, workspaceDatabaseConfiguration.getDataSource());
    }

    // The JobService initialization also handles Stairway initialization.
    JobService jobService = applicationContext.getBean(JobService.class);
    jobService.initialize();

    // TODO: Fill in this method with any other initialization that needs to happen
    //  between the point of having the entire application initialized and
    //  the point of opening the port to start accepting REST requests.

    initializeStairwayComponent(applicationContext);
  }

  private static void initializeStairwayComponent(ApplicationContext applicationContext) {
    final StairwayComponent stairwayComponent = applicationContext.getBean(StairwayComponent.class);
    final StairwayDatabaseConfiguration stairwayDatabaseConfiguration =
        applicationContext.getBean(StairwayDatabaseConfiguration.class);
    final DataSource dataSource = stairwayDatabaseConfiguration.getDataSource();
    final MdcHook mdcHook = applicationContext.getBean(MdcHook.class);
    final TracingHook tracingHook = new TracingHook();
    final FlightBeanBag flightBeanBag = applicationContext.getBean(FlightBeanBag.class);
    stairwayComponent.initialize(dataSource, flightBeanBag, ImmutableList.of(mdcHook, tracingHook));
  }
}
