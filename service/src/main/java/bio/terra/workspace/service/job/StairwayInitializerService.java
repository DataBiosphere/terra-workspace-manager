package bio.terra.workspace.service.job;

import bio.terra.common.db.DataSourceManager;
import bio.terra.common.stairway.MonitoringHook;
import bio.terra.common.stairway.StairwayComponent;
import bio.terra.common.stairway.StairwayLoggingHook;
import bio.terra.stairway.StairwayMapper;
import bio.terra.workspace.app.configuration.external.StairwayDatabaseConfiguration;
import bio.terra.workspace.common.logging.FlightMetricsHook;
import bio.terra.workspace.common.logging.WorkspaceActivityLogHook;
import bio.terra.workspace.common.utils.FlightBeanBag;
import bio.terra.workspace.service.resource.controlled.model.ControlledResource;
import bio.terra.workspace.service.resource.model.WsmResource;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.introspect.AnnotatedMember;
import com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector;
import io.opentelemetry.api.OpenTelemetry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class StairwayInitializerService {

  private final DataSourceManager dataSourceManager;
  private final StairwayDatabaseConfiguration stairwayDatabaseConfiguration;
  private final WorkspaceActivityLogHook workspaceActivityLogHook;
  private final StairwayComponent stairwayComponent;
  private final FlightBeanBag flightBeanBag;
  private final ObjectMapper objectMapper;
  private final OpenTelemetry openTelemetry;
  private final FlightMetricsHook flightMetricsHook;

  @Autowired
  public StairwayInitializerService(
      DataSourceManager dataSourceManager,
      StairwayDatabaseConfiguration stairwayDatabaseConfiguration,
      WorkspaceActivityLogHook workspaceActivityLogHook,
      StairwayComponent stairwayComponent,
      FlightBeanBag flightBeanBag,
      ObjectMapper objectMapper,
      OpenTelemetry openTelemetry,
      FlightMetricsHook flightMetricsHook) {
    this.dataSourceManager = dataSourceManager;
    this.stairwayDatabaseConfiguration = stairwayDatabaseConfiguration;
    this.workspaceActivityLogHook = workspaceActivityLogHook;
    this.stairwayComponent = stairwayComponent;
    this.flightBeanBag = flightBeanBag;
    this.objectMapper = objectMapper;
    this.openTelemetry = openTelemetry;
    this.flightMetricsHook = flightMetricsHook;
  }

  /**
   * This method is called from StartupInitializer as part of the sequence of migrating databases
   * and recovering any jobs; i.e., Stairway flights.
   */
  public void initialize() {
    configureMapper();
    stairwayComponent.initialize(
        stairwayComponent
            .newStairwayOptionsBuilder()
            .dataSource(dataSourceManager.initializeDataSource(stairwayDatabaseConfiguration))
            .context(flightBeanBag)
            .addHook(new StairwayLoggingHook())
            .addHook(new MonitoringHook(openTelemetry))
            .addHook(flightMetricsHook)
            .addHook(workspaceActivityLogHook)
            .exceptionSerializer(new StairwayExceptionSerializer(objectMapper)));
  }

  /**
   * This is currently a hack, because Stairway does not provide a way to pass in the mapper. It
   * does expose its own mapper for testing, so we use that public API to add the introspector that
   * we need.
   *
   * <p>TODO: PF-2505 When that Stairway feature is done we should create and set our own object
   * mapper in Stairway.
   */
  private static void configureMapper() {
    StairwayMapper.getObjectMapper().setAnnotationIntrospector(new IgnoreInheritedIntrospector());
  }

  /**
   * Jackson does not see @JsonIgnore annotations from super classes. That means any getter in a
   * super class gets serialized by default. We do not want that behavior, so we add this
   * introspector to the ObjectMapper to force ignore everything from the resource super classes.
   *
   * <p>We do not need to ignore ReferencedResource class because it does not add any fields to
   * those coming from WsmResource class.
   */
  private static class IgnoreInheritedIntrospector extends JacksonAnnotationIntrospector {
    @Override
    public boolean hasIgnoreMarker(AnnotatedMember m) {
      boolean ignore =
          (m.getDeclaringClass() == WsmResource.class)
              || (m.getDeclaringClass() == ControlledResource.class);
      return ignore || super.hasIgnoreMarker(m);
    }
  }
}
