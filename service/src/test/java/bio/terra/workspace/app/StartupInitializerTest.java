package bio.terra.workspace.app;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import bio.terra.common.db.DataSourceManager;
import bio.terra.common.migrate.LiquibaseMigrator;
import bio.terra.landingzone.job.LandingZoneJobService;
import bio.terra.landingzone.library.configuration.LandingZoneDatabaseConfiguration;
import bio.terra.workspace.app.configuration.external.BufferServiceConfiguration;
import bio.terra.workspace.app.configuration.external.FeatureConfiguration;
import bio.terra.workspace.app.configuration.external.WorkspaceDatabaseConfiguration;
import bio.terra.workspace.common.annotations.Unit;
import bio.terra.workspace.service.buffer.BufferService;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.job.StairwayInitializerService;
import bio.terra.workspace.service.policy.TpsApiDispatch;
import bio.terra.workspace.service.workspace.WsmApplicationService;
import javax.sql.DataSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationContext;

@Unit
@ExtendWith(MockitoExtension.class)
class StartupInitializerTest {

  @Mock private ApplicationContext context;
  @Mock private DataSourceManager dataSourceManager;
  @Mock private WorkspaceDatabaseConfiguration workspaceDatabaseConfiguration;
  @Mock private LiquibaseMigrator liquibaseMigrator;
  @Mock private StairwayInitializerService stairwayInitializerService;
  @Mock private WsmApplicationService wsmApplicationService;
  @Mock private FeatureConfiguration featureConfiguration;
  @Mock private LandingZoneDatabaseConfiguration landingZoneDatabaseConfiguration;
  @Mock private LandingZoneJobService landingZoneJobService;
  @Mock private DataSource wsmDataSource;
  @Mock private DataSource lzsDataSource;
  @Mock private SamService samService;
  @Mock private TpsApiDispatch tpsApiDispatch;
  @Mock private BufferService bufferService;
  @Mock private BufferServiceConfiguration bufferServiceConfiguration;

  private enum DatabaseInitializationInstruction {
    INITIALIZE,
    UPGRADE,
    CONTINUE
  }

  @ParameterizedTest
  @EnumSource(value = DatabaseInitializationInstruction.class)
  void initialize(DatabaseInitializationInstruction dbInstruction) throws InterruptedException {
    when(context.getBean(DataSourceManager.class)).thenReturn(dataSourceManager);
    when(context.getBean(WorkspaceDatabaseConfiguration.class))
        .thenReturn(workspaceDatabaseConfiguration);
    when(context.getBean(LiquibaseMigrator.class)).thenReturn(liquibaseMigrator);
    when(context.getBean(StairwayInitializerService.class)).thenReturn(stairwayInitializerService);
    when(context.getBean(WsmApplicationService.class)).thenReturn(wsmApplicationService);
    when(context.getBean(FeatureConfiguration.class)).thenReturn(featureConfiguration);
    when(context.getBean(LandingZoneDatabaseConfiguration.class))
        .thenReturn(landingZoneDatabaseConfiguration);
    when(context.getBean("landingZoneJobService", LandingZoneJobService.class))
        .thenReturn(landingZoneJobService);
    when(context.getBean(BufferServiceConfiguration.class)).thenReturn(bufferServiceConfiguration);
    when(context.getBean(BufferService.class)).thenReturn(bufferService);
    when(context.getBean(TpsApiDispatch.class)).thenReturn(tpsApiDispatch);

    when(featureConfiguration.isTpsEnabled()).thenReturn(true);
    when(bufferServiceConfiguration.getEnabled()).thenReturn(true);
    when(dataSourceManager.initializeDataSource(workspaceDatabaseConfiguration))
        .thenReturn(wsmDataSource);

    switch (dbInstruction) {
      case INITIALIZE -> {
        when(workspaceDatabaseConfiguration.isInitializeOnStart()).thenReturn(true);
        when(landingZoneDatabaseConfiguration.isInitializeOnStart()).thenReturn(true);
        when(landingZoneDatabaseConfiguration.getDataSource()).thenReturn(lzsDataSource);
      }
      case UPGRADE -> {
        when(workspaceDatabaseConfiguration.isUpgradeOnStart()).thenReturn(true);
        when(landingZoneDatabaseConfiguration.isUpgradeOnStart()).thenReturn(true);
        when(landingZoneDatabaseConfiguration.getDataSource()).thenReturn(lzsDataSource);
      }
      case CONTINUE -> {
        // No additional mocking.
      }
    }

    StartupInitializer.initialize(context);

    verify(featureConfiguration).logFeatures();
    switch (dbInstruction) {
      case INITIALIZE -> {
        verify(liquibaseMigrator).initialize(anyString(), eq(wsmDataSource));
        verify(liquibaseMigrator).initialize(anyString(), eq(lzsDataSource));
      }
      case UPGRADE -> {
        verify(liquibaseMigrator).upgrade(anyString(), eq(wsmDataSource));
        verify(liquibaseMigrator).upgrade(anyString(), eq(lzsDataSource));
      }
      case CONTINUE -> verifyNoInteractions(liquibaseMigrator);
    }
    verify(stairwayInitializerService).initialize();
    verify(wsmApplicationService).configure();
    verify(landingZoneJobService).initialize();
  }
}
