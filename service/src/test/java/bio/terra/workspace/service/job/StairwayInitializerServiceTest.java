package bio.terra.workspace.service.job;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import bio.terra.common.db.DataSourceManager;
import bio.terra.common.stairway.MonitoringHook;
import bio.terra.common.stairway.StairwayComponent;
import bio.terra.workspace.app.configuration.external.StairwayDatabaseConfiguration;
import bio.terra.workspace.common.annotations.Unit;
import bio.terra.workspace.common.logging.WorkspaceActivityLogHook;
import bio.terra.workspace.common.utils.FlightBeanBag;
import bio.terra.workspace.common.utils.StairwayLoggingHook;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.opentelemetry.api.OpenTelemetry;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@Unit
@ExtendWith(MockitoExtension.class)
class StairwayInitializerServiceTest {

  @Mock private DataSourceManager dataSourceManager;
  @Mock private StairwayDatabaseConfiguration stairwayDatabaseConfiguration;
  @Mock private StairwayLoggingHook stairwayLoggingHook;
  @Mock private WorkspaceActivityLogHook workspaceActivityLogHook;
  @Mock private StairwayComponent stairwayComponent;
  @Mock private FlightBeanBag flightBeanBag;
  private StairwayInitializerService stairwayInitializerService;

  @BeforeEach
  void beforeEach() {
    stairwayInitializerService =
        new StairwayInitializerService(
            dataSourceManager,
            stairwayDatabaseConfiguration,
            stairwayLoggingHook,
            workspaceActivityLogHook,
            stairwayComponent,
            flightBeanBag,
            mock(ObjectMapper.class),
            OpenTelemetry.noop());
  }

  @Test
  void initialize() {
    var stairwayOptionsBuilder = new StairwayComponent.StairwayOptionsBuilder();
    when(stairwayComponent.newStairwayOptionsBuilder()).thenReturn(stairwayOptionsBuilder);
    var dataSource = mock(DataSource.class);
    when(dataSourceManager.initializeDataSource(stairwayDatabaseConfiguration))
        .thenReturn(dataSource);

    stairwayInitializerService.initialize();

    // Stairway is initialized with our Stairway options builder
    verify(stairwayComponent).initialize(stairwayOptionsBuilder);
    assertThat(
        "Stairway is initialized with data source",
        stairwayOptionsBuilder.getDataSource(),
        is(dataSource));
    assertThat(
        "Stairway is initialized with flight bean bag context",
        stairwayOptionsBuilder.getContext(),
        is(flightBeanBag));
    assertThat(
        "Stairway is initialized with logging, monitoring, and activity log hooks",
        stairwayOptionsBuilder.getHooks(),
        contains(
            is(stairwayLoggingHook),
            instanceOf(MonitoringHook.class),
            is(workspaceActivityLogHook)));
    assertThat(
        "Stairway is initialized with exception serializer",
        stairwayOptionsBuilder.getExceptionSerializer(),
        instanceOf(StairwayExceptionSerializer.class));
  }
}
