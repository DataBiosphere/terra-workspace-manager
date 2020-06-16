package bio.terra.workspace.service.status;

import bio.terra.workspace.app.configuration.DataRepoConfig;
import bio.terra.workspace.common.utils.BaseStatusService;
import bio.terra.workspace.common.utils.StatusSubsystem;
import bio.terra.workspace.generated.model.SystemStatusSystems;
import bio.terra.workspace.service.datarepo.DataRepoService;
import bio.terra.workspace.service.iam.SamService;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class WorkspaceManagerStatusService extends BaseStatusService {

  @Autowired
  public WorkspaceManagerStatusService(
      DataRepoService dataRepoService,
      DataRepoConfig dataRepoConfig,
      NamedParameterJdbcTemplate jdbcTemplate,
      SamService samService) {

    Supplier<SystemStatusSystems> dbHealthFn =
        () ->
            new SystemStatusSystems()
                .ok(jdbcTemplate.getJdbcTemplate().execute(this::isConnectionValid));
    registerSubsystem("Postgres", new StatusSubsystem(dbHealthFn, /*isCritical=*/ true));

    // TODO: should we really be checking every instance?
    for (Map.Entry<String, String> instanceEntry : dataRepoConfig.getInstances().entrySet()) {
      Supplier<SystemStatusSystems> checkDataRepoInstanceFn =
          () -> dataRepoService.status(instanceEntry.getValue());
      registerSubsystem(
          "Data Repo instance: " + instanceEntry.getKey(),
          new StatusSubsystem(checkDataRepoInstanceFn, /*isCritical=*/ false));
    }

    registerSubsystem("Sam", new StatusSubsystem(samService::status, /*isCritical=*/ true));
  }

  private Boolean isConnectionValid(Connection connection) throws SQLException {
    return connection.isValid(0);
  }
}
