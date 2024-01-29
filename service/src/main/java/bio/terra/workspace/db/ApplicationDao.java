package bio.terra.workspace.db;

import bio.terra.common.db.ReadTransaction;
import bio.terra.common.db.WriteTransaction;
import bio.terra.workspace.db.exception.ApplicationInUseException;
import bio.terra.workspace.db.exception.ApplicationNotFoundException;
import bio.terra.workspace.db.exception.InvalidApplicationStateException;
import bio.terra.workspace.service.workspace.model.WsmApplication;
import bio.terra.workspace.service.workspace.model.WsmApplicationState;
import bio.terra.workspace.service.workspace.model.WsmWorkspaceApplication;
import com.google.common.annotations.VisibleForTesting;
import java.util.List;
import java.util.UUID;
import org.apache.commons.lang3.StringUtils;
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
              .applicationId(rs.getString("application_id"))
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
                .applicationId(rs.getString("application_id"))
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

  // Query for finding applications that should be enabled on the target workspace
  private static final String WORKSPACE_APPLICATION_ID_QUERY =
      """
      SELECT A.application_id FROM enabled_application W, application A
      WHERE
        W.workspace_id = :workspace_id AND
        W.application_id = A.application_id AND
        A.state = :operating;
      """;

  private static final RowMapper<String> WORKSPACE_APPLICATION_ID_ROW_MAPPER =
      (rs, rowNum) -> rs.getString("application_id");

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
  public boolean applicationInUse(String applicationId) {
    final String sql = "SELECT COUNT(*) FROM resource WHERE associated_app = :application_id";

    MapSqlParameterSource params =
        new MapSqlParameterSource().addValue("application_id", applicationId);

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
            .addValue("application_id", app.getApplicationId())
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
   * @param workspaceUuid workspace of interest
   * @param applicationId application of interest
   * @return workspace-application object
   */
  @WriteTransaction
  public WsmWorkspaceApplication disableWorkspaceApplication(
      UUID workspaceUuid, String applicationId) {

    // Validate that the application exists; workspace is validated in layers above this
    getApplicationOrThrow(applicationId);

    // It is an error to have application resources in the workspace if we are disabling it.
    final String countAppUsesSql =
        "SELECT COUNT(*) FROM resource"
            + " WHERE associated_app = :application_id AND workspace_id = :workspace_id";

    MapSqlParameterSource params =
        new MapSqlParameterSource()
            .addValue("workspace_id", workspaceUuid.toString())
            .addValue("application_id", applicationId);

    Integer count = jdbcTemplate.queryForObject(countAppUsesSql, params, Integer.class);
    if (count != null && count > 0) {
      throw new ApplicationInUseException(
          String.format("Application %s in use in workspace %s", applicationId, workspaceUuid));
    }

    // No uses, so we disable
    final String sql =
        "DELETE FROM enabled_application"
            + " WHERE workspace_id = :workspace_id AND application_id = :application_id";

    int rowCount = jdbcTemplate.update(sql, params);
    if (rowCount > 0) {
      logger.info(
          "Deleted record enabling application {} for workspace {}", applicationId, workspaceUuid);
    } else {
      logger.info(
          "Ignoring duplicate disabling of application {} for workspace {}",
          applicationId,
          workspaceUuid);
    }

    return getWorkspaceApplicationWorker(workspaceUuid, applicationId);
  }

  /**
   * Enable an application in a workspace. It is not an error to enable an already enabled
   * application.
   *
   * @param workspaceUuid workspace of interest
   * @param applicationId application of interest
   * @return workspace-application object
   */
  @WriteTransaction
  public WsmWorkspaceApplication enableWorkspaceApplication(
      UUID workspaceUuid, String applicationId) {

    WsmApplication application = getApplicationOrThrow(applicationId);
    if (application.getState() != WsmApplicationState.OPERATING) {
      throw new InvalidApplicationStateException(
          "Applications is " + application.getState().toApi() + " and cannot be enabled");
    }

    return enableWorkspaceApplicationWorker(workspaceUuid, applicationId);
  }

  /**
   * Enable an application in a workspace - do not perform the application state check. This is only
   * used in testing.
   *
   * <p>It is not an error to enable an already enabled application.
   *
   * @param workspaceUuid workspace of interest
   * @param applicationId application of interest
   * @return workspace-application object
   */
  @VisibleForTesting
  @WriteTransaction
  public WsmWorkspaceApplication enableWorkspaceApplicationNoCheck(
      UUID workspaceUuid, String applicationId) {

    return enableWorkspaceApplicationWorker(workspaceUuid, applicationId);
  }

  private WsmWorkspaceApplication enableWorkspaceApplicationWorker(
      UUID workspaceUuid, String applicationId) {

    final String sql =
        "INSERT INTO enabled_application (workspace_id, application_id)"
            + " VALUES (:workspace_id, :application_id)";

    MapSqlParameterSource params =
        new MapSqlParameterSource()
            .addValue("workspace_id", workspaceUuid.toString())
            .addValue("application_id", applicationId);

    try {
      jdbcTemplate.update(sql, params);
      logger.info(
          "Inserted record enabling application {} for workspace {}", applicationId, workspaceUuid);
    } catch (DuplicateKeyException e) {
      logger.info(
          "Ignoring duplicate enabling application {} for workspace {}",
          applicationId,
          workspaceUuid);
    }

    return getWorkspaceApplicationWorker(workspaceUuid, applicationId);
  }

  /**
   * Return the state of a specific application viz a workspace
   *
   * @param workspaceUuid workspace of interest
   * @param applicationId application of interest
   * @return workspace-application object
   */
  @ReadTransaction
  public WsmWorkspaceApplication getWorkspaceApplication(UUID workspaceUuid, String applicationId) {
    return getWorkspaceApplicationWorker(workspaceUuid, applicationId);
  }

  @ReadTransaction
  public List<WsmWorkspaceApplication> listWorkspaceApplications(
      UUID workspaceUuid, int offset, int limit) {

    final String sql = WORKSPACE_APPLICATION_QUERY + " OFFSET :offset LIMIT :limit";

    var params =
        new MapSqlParameterSource()
            .addValue("workspace_id", workspaceUuid.toString())
            .addValue("offset", offset)
            .addValue("limit", limit);

    List<WsmWorkspaceApplication> resultList =
        jdbcTemplate.query(sql, params, WORKSPACE_APPLICATION_ROW_MAPPER);

    for (WsmWorkspaceApplication result : resultList) {
      result.workspaceUuid(workspaceUuid);
    }
    return resultList;
  }

  @ReadTransaction
  public List<String> listWorkspaceApplicationsForClone(UUID workspaceUuid) {
    var params =
        new MapSqlParameterSource()
            .addValue("workspace_id", workspaceUuid.toString())
            .addValue("operating", WsmApplicationState.OPERATING.toDb());

    return jdbcTemplate.query(
        WORKSPACE_APPLICATION_ID_QUERY, params, WORKSPACE_APPLICATION_ID_ROW_MAPPER);
  }

  // internal workspace application lookup
  private WsmWorkspaceApplication getWorkspaceApplicationWorker(
      UUID workspaceUuid, String applicationId) {
    final String sql = WORKSPACE_APPLICATION_QUERY + " WHERE A.application_id = :application_id";

    var params =
        new MapSqlParameterSource()
            .addValue("application_id", applicationId)
            .addValue("workspace_id", workspaceUuid.toString());

    try {
      WsmWorkspaceApplication result =
          DataAccessUtils.requiredSingleResult(
              jdbcTemplate.query(sql, params, WORKSPACE_APPLICATION_ROW_MAPPER));
      result.workspaceUuid(workspaceUuid);
      return result;
    } catch (EmptyResultDataAccessException e) {
      throw new ApplicationNotFoundException(
          String.format("Application %s not found.", applicationId));
    }
  }

  /**
   * Retrieve a configured application
   *
   * @param applicationId id of the application to get
   * @return WsmApplication
   * @throws ApplicationNotFoundException when application is not found
   */
  @ReadTransaction
  public WsmApplication getApplication(String applicationId) throws ApplicationNotFoundException {
    return getApplicationOrThrow(applicationId);
  }

  // internal application lookup
  private WsmApplication getApplicationOrThrow(String applicationId) {
    final String sql = APPLICATION_QUERY + " WHERE application_id = :application_id";

    var params = new MapSqlParameterSource().addValue("application_id", applicationId);

    try {
      return DataAccessUtils.requiredSingleResult(
          jdbcTemplate.query(sql, params, APPLICATION_ROW_MAPPER));
    } catch (EmptyResultDataAccessException e) {
      throw new ApplicationNotFoundException(
          String.format("Application %s not found.", applicationId));
    }
  }

  @ReadTransaction
  public WsmApplication getApplicationByEmail(String email) {
    final String sql = APPLICATION_QUERY + " WHERE service_account = :email";
    var params = new MapSqlParameterSource().addValue("email", StringUtils.lowerCase(email));

    try {
      return DataAccessUtils.requiredSingleResult(
          jdbcTemplate.query(sql, params, APPLICATION_ROW_MAPPER));
    } catch (EmptyResultDataAccessException e) {
      throw new ApplicationNotFoundException("Requester is not a configured application: " + email);
    }
  }

  /**
   * @return List of all applications in the database
   */
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
            .addValue("application_id", app.getApplicationId())
            .addValue("display_name", app.getDisplayName())
            .addValue("description", app.getDescription())
            .addValue(SERVICE_ACCOUNT, app.getServiceAccount())
            .addValue("state", app.getState().toDb());

    jdbcTemplate.update(sql, params);
  }
}
