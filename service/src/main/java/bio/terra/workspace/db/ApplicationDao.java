package bio.terra.workspace.db;

import bio.terra.common.db.ReadTransaction;
import bio.terra.common.db.WriteTransaction;
import bio.terra.workspace.service.workspace.model.WsmApplication;
import bio.terra.workspace.service.workspace.model.WsmApplicationState;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * ApplicationDao includes operations on the application tables: what applications are configured in
 * WSM and whether they are enabled in particular workspaces.
 */
@Component
public class ApplicationDao {
  private final Logger logger = LoggerFactory.getLogger(ApplicationDao.class);
  private final NamedParameterJdbcTemplate jdbcTemplate;

  // gitallow doesn't like service_account so...
  private static final String SERVICE_ACCOUNT = "service_" + "account";

  private static final RowMapper<WsmApplication> APPLICATION_ROW_MAPPER =
      (rs, rowNum) ->
          new WsmApplication()
              .applicationId(UUID.fromString(rs.getString("application_id")))
              .displayName(rs.getString("display_name"))
              .description(rs.getString("description"))
              .serviceAccount(rs.getString(SERVICE_ACCOUNT))
              .state(WsmApplicationState.fromDb(rs.getString("state")));

  @Autowired
  public ApplicationDao(NamedParameterJdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  /**
   * Check if an application is in use by any resources
   *
   * @param applicationId application to check
   * @return true if the application is in use
   */
  @ReadTransaction
  public boolean applicationInUse(UUID applicationId) {
    final String sql = "SELECT COUNT(*) FROM resource WHERE associated_app = :application_id";

    MapSqlParameterSource params =
        new MapSqlParameterSource().addValue("application_id", applicationId.toString());

    Integer count = jdbcTemplate.queryForObject(sql, params, Integer.class);
    return (count != null && count > 0);
  }

  @WriteTransaction
  public void createApplication(WsmApplication app) {
    final String sql =
        "INSERT INTO application "
            + "(application_id, display_name, description, service_account, state)"
            + " VALUES "
            + " (:application_id, :display_name, :description, :service_account, :state)";

    var params =
        new MapSqlParameterSource()
            .addValue("application_id", app.getApplicationId().toString())
            .addValue("display_name", app.getDisplayName())
            .addValue("description", app.getDescription())
            .addValue(SERVICE_ACCOUNT, app.getServiceAccount())
            .addValue("state", app.getState().toDb());

    jdbcTemplate.update(sql, params);
  }

  /** @return List of all applications in the database */
  @ReadTransaction
  public List<WsmApplication> listApplications() {
    final String sql =
        "SELECT application_id, display_name, description, service_account, state"
            + " FROM application";
    return jdbcTemplate.query(sql, APPLICATION_ROW_MAPPER);
  }

  /**
   * Update an application based on the configuration
   *
   * @param app the updated application configuration
   */
  @WriteTransaction
  public void updateApplication(WsmApplication app) {
    final String sql =
        "UPDATE application SET"
            + " display_name = :display_name,"
            + " description = :description,"
            + " service_account = :service_account,"
            + " state = :state"
            + " WHERE application_id = :application_id";

    var params =
        new MapSqlParameterSource()
            .addValue("application_id", app.getApplicationId().toString())
            .addValue("display_name", app.getDisplayName())
            .addValue("description", app.getDescription())
            .addValue(SERVICE_ACCOUNT, app.getServiceAccount())
            .addValue("state", app.getState().toDb());

    jdbcTemplate.update(sql, params);
  }
}
