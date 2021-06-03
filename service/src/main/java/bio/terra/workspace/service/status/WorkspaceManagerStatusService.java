package bio.terra.workspace.service.status;

import bio.terra.workspace.app.configuration.external.StatusCheckConfiguration;
import bio.terra.workspace.service.iam.SamService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class WorkspaceManagerStatusService extends BaseStatusService {
  private static final Logger logger = LoggerFactory.getLogger(WorkspaceManagerStatusService.class);
  private final int databaseCheckTimeout;
  private final NamedParameterJdbcTemplate jdbcTemplate;

  @Autowired
  public WorkspaceManagerStatusService(
      NamedParameterJdbcTemplate jdbcTemplate,
      SamService samService,
      StatusCheckConfiguration configuration) {
    super(configuration);
    // Heuristic for database timeout - half of the polling interval
    this.databaseCheckTimeout = configuration.getPollingIntervalSeconds() / 2;
    this.jdbcTemplate = jdbcTemplate;
    super.registerStatusCheck("CloudSQL", this::isConnectionValid);
    super.registerStatusCheck("Sam", samService::status);
  }

  private Boolean isConnectionValid() {
    try {
      return jdbcTemplate
          .getJdbcTemplate()
          .getDataSource()
          .getConnection()
          .isValid(databaseCheckTimeout);
    } catch (Exception e) {
      logger.warn("Database status check failed", e);
    }
    return false;
  }
}
