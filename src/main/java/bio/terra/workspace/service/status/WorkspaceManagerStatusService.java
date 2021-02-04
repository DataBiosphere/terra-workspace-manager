package bio.terra.workspace.service.status;

import bio.terra.workspace.app.configuration.external.DataRepoConfiguration;
import bio.terra.workspace.common.utils.BaseStatusService;
import bio.terra.workspace.common.utils.StatusSubsystem;
import bio.terra.workspace.generated.model.SystemStatusSystems;
import bio.terra.workspace.service.buffer.BufferService;
import bio.terra.workspace.service.datarepo.DataRepoService;
import bio.terra.workspace.service.iam.SamService;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class WorkspaceManagerStatusService extends BaseStatusService {

  private final NamedParameterJdbcTemplate jdbcTemplate;

  @Autowired
  public WorkspaceManagerStatusService(
      DataRepoService dataRepoService,
      DataRepoConfiguration dataRepoConfiguration,
      NamedParameterJdbcTemplate jdbcTemplate,
      SamService samService,
      BufferService bufferService,
      @Value("${workspace.status-check.staleness-threshold-ms}") long staleThresholdMillis) {
    super(staleThresholdMillis);
    this.jdbcTemplate = jdbcTemplate;
    Supplier<SystemStatusSystems> dbHealthFn =
        () ->
            new SystemStatusSystems()
                .ok(jdbcTemplate.getJdbcTemplate().execute(this::isConnectionValid));
    registerSubsystem("Postgres", new StatusSubsystem(dbHealthFn, /*isCritical=*/ true));

    for (Map.Entry<String, String> instanceEntry :
        dataRepoConfiguration.getInstances().entrySet()) {
      Supplier<SystemStatusSystems> checkDataRepoInstanceFn =
          () -> dataRepoService.status(instanceEntry.getValue());
      registerSubsystem(
          "Data Repo instance: " + instanceEntry.getKey(),
          new StatusSubsystem(checkDataRepoInstanceFn, /*isCritical=*/ false));
    }

    Supplier<SystemStatusSystems> samStatusFn = () -> samService.status();
    registerSubsystem("Sam", new StatusSubsystem(samStatusFn, /*isCritical=*/ true));

    Supplier<SystemStatusSystems> bufferHealthFn = () -> bufferService.status();
    registerSubsystem("Buffer", new StatusSubsystem(bufferHealthFn, true));
  }

  private Boolean isConnectionValid(Connection connection) throws SQLException {
    return connection.isValid(0);
  }
}
