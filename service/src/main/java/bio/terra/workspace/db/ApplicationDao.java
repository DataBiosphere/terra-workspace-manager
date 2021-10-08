package bio.terra.workspace.db;

import bio.terra.common.db.ReadTransaction;
import bio.terra.common.db.WriteTransaction;
import bio.terra.workspace.service.workspace.model.WsmApplication;
import bio.terra.workspace.service.workspace.model.WsmApplicationState;
import bio.terra.workspace.db.exception.ApplicationNotFoundException;
import bio.terra.workspace.db.exception.InvalidApplicationStateException;
import bio.terra.workspace.service.workspace.model.WsmApplication;
import bio.terra.workspace.service.workspace.model.WsmApplicationState;
import bio.terra.workspace.service.workspace.model.WsmWorkspaceApplication;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.dao.support.DataAccessUtils;
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

  private static final String APPLICATION_QUERY =
      "SELECT application_id, display_name, description, service_account, state"
          + " FROM application";

  private static final RowMapper<WsmWorkspaceApplication> WORKSPACE_APPLICATION_ROW_MAPPER =
      (rs, rowNum) -> {
        var wsmApp =
            new WsmApplication()
                .applicationId(UUID.fromString(rs.getString("application_id")))
                .displayName(rs.getString("display_name"))
                .description(rs.getString("description"))
                .serviceAccount(rs.getString(SERVICE_ACCOUNT))
                .state(WsmApplicationState.fromDb(rs.getString("state")));
        return new WsmWorkspaceApplication().application(wsmApp).enabled(rs.getInt("ecount") > 0);
      };

  private static final String WORKSPACE_APPLICATION_QUERY =
      "SELECT A.application_id, A.display_name, A.description, A.service_account, A.state,"
          + " (SELECT COUNT(*)"
          + "  FROM enabled_application W"
          + "  WHERE W.application_id = A.application_id AND workspace_id = :workspace_id) AS ecount"
          + " FROM application A";

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

  /**
   * Disable an application in a workspace. It is not an error to disable an already disabled
   * application.
   *
   * @param workspaceId workspace of interest
   * @param applicationId application of interest
   * @return workspace-application object
   */
  @WriteTransaction
  public WsmWorkspaceApplication disableWorkspaceApplication(UUID workspaceId, UUID applicationId) {

    // Validate that the application exists
    getApplication(applicationId);

    final String sql =
        "DELETE FROM enabled_application"
            + " WHERE workspace_id = workspace_id AND application_id = :application_id";

    MapSqlParameterSource params =
        new MapSqlParameterSource()
            .addValue("workspace_id", workspaceId.toString())
            .addValue("application_id", applicationId.toString());

    int rowCount = jdbcTemplate.update(sql, params);
    if (rowCount > 0) {
      logger.info(
          "Deleted record enabling application {} for workspace {}", applicationId, workspaceId);
    } else {
      logger.info(
          "Ignoring duplicate disabling of application {} for workspace {}",
          applicationId,
          workspaceId);
    }

    return getWorkspaceApplicationWorker(workspaceId, applicationId);
  }

  /**
   * Enable an application in a workspace. It is not an error to enable an already enabled
   * application.
   *
   * @param workspaceId workspace of interest
   * @param applicationId application of interest
   * @return workspace-application object
   */
  @WriteTransaction
  public WsmWorkspaceApplication enableWorkspaceApplication(UUID workspaceId, UUID applicationId) {

    WsmApplication application = getApplication(applicationId);
    if (application.getState() != WsmApplicationState.OPERATING) {
      throw new InvalidApplicationStateException(
          "Applications is DEPRECATED or DECOMMISSIONED and cannot be enabled");
    }

    return enableWorkspaceApplicationWorker(workspaceId, applicationId);
  }

  /**
   * Enable an application in a workspace - do not perform the application state check. This is used
   * to restore an application if the disable application flight fails.
   *
   * <p>It is not an error to enable an already enabled application.
   *
   * @param workspaceId workspace of interest
   * @param applicationId application of interest
   * @return workspace-application object
   */
  @WriteTransaction
  public WsmWorkspaceApplication enableWorkspaceApplicationNoCheck(
      UUID workspaceId, UUID applicationId) {

    return enableWorkspaceApplicationWorker(workspaceId, applicationId);
  }

  private WsmWorkspaceApplication enableWorkspaceApplicationWorker(
      UUID workspaceId, UUID applicationId) {

    final String sql =
        "INSERT INTO enabled_application (workspace_id, application_id)"
            + " VALUES (:workspace_id, :application_id)";

    MapSqlParameterSource params =
        new MapSqlParameterSource()
            .addValue("workspace_id", workspaceId.toString())
            .addValue("application_id", applicationId.toString());

    try {
      jdbcTemplate.update(sql, params);
      logger.info(
          "Inserted record enabling application {} for workspace {}", applicationId, workspaceId);
    } catch (DuplicateKeyException e) {
      logger.info(
          "Ignoring duplicate enabling application {} for workspace {}",
          applicationId,
          workspaceId);
    }

    return getWorkspaceApplicationWorker(workspaceId, applicationId);
  }

  /**
   * Return the state of a specific application viz a workspace
   *
   * @param workspaceId workspace of interest
   * @param applicationId application of interest
   * @return workspace-application object
   */
  @ReadTransaction
  public WsmWorkspaceApplication getWorkspaceApplication(UUID workspaceId, UUID applicationId) {
    return getWorkspaceApplicationWorker(workspaceId, applicationId);
  }

  @ReadTransaction
  public List<WsmWorkspaceApplication> listWorkspaceApplications(
      UUID workspaceId, int offset, int limit) {

    final String sql = WORKSPACE_APPLICATION_QUERY + " OFFSET :offset LIMIT :limit";

    var params =
        new MapSqlParameterSource()
            .addValue("workspace_id", workspaceId.toString())
            .addValue("offset", offset)
            .addValue("limit", limit);

    List<WsmWorkspaceApplication> resultList =
        jdbcTemplate.query(sql, params, WORKSPACE_APPLICATION_ROW_MAPPER);

    for (WsmWorkspaceApplication result : resultList) {
      result.workspaceId(workspaceId);
    }
    return resultList;
  }

  // internal workspace application lookup
  private WsmWorkspaceApplication getWorkspaceApplicationWorker(
      UUID workspaceId, UUID applicationId) {
    final String sql = WORKSPACE_APPLICATION_QUERY + " WHERE A.application_id = :application_id";

    var params =
        new MapSqlParameterSource()
            .addValue("application_id", applicationId.toString())
            .addValue("workspace_id", workspaceId.toString());

    try {
      WsmWorkspaceApplication result =
          DataAccessUtils.requiredSingleResult(
              jdbcTemplate.query(sql, params, WORKSPACE_APPLICATION_ROW_MAPPER));
      result.workspaceId(workspaceId);
      return result;
    } catch (EmptyResultDataAccessException e) {
      throw new ApplicationNotFoundException(
          String.format("Application %s not found.", applicationId.toString()));
    }
  }

  // internal application lookup
  private WsmApplication getApplication(UUID applicationId) {
    final String sql = APPLICATION_QUERY + " WHERE application_id = :application_id";

    var params = new MapSqlParameterSource().addValue("application_id", applicationId.toString());

    try {
      return DataAccessUtils.requiredSingleResult(
          jdbcTemplate.query(sql, params, APPLICATION_ROW_MAPPER));
    } catch (EmptyResultDataAccessException e) {
      throw new ApplicationNotFoundException(
          String.format("Application %s not found.", applicationId.toString()));
    }
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
