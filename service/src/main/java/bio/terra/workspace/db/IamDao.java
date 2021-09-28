package bio.terra.workspace.db;

import bio.terra.common.db.ReadTransaction;
import bio.terra.common.db.WriteTransaction;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.model.WsmIamRole;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.dao.support.DataAccessUtils;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/** IamDao does the database work supporting the Azure PoC */
@Component
public class IamDao {
  private final NamedParameterJdbcTemplate jdbcTemplate;
  private final Logger logger = LoggerFactory.getLogger(IamDao.class);

  @Autowired
  public IamDao(NamedParameterJdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  /**
   * Delete all of the grants for the workspace. We never clean users.
   *
   * @param workspaceId unique identifier of the workspace
   */
  @WriteTransaction
  public void deleteWorkspace(UUID workspaceId) {
    final String sql = "DELETE FROM poc_grant WHERE workspace_id = :id";

    MapSqlParameterSource params =
        new MapSqlParameterSource().addValue("id", workspaceId.toString());
    int rowsAffected = jdbcTemplate.update(sql, params);
    boolean deleted = rowsAffected > 0;

    if (deleted) {
      logger.info("Deleted grant for workspace {}", workspaceId);
    } else {
      logger.info("No grant found for delete workspace {}", workspaceId);
    }
  }

  /**
   * Support for isAuthorized. Get the workspace id from the resource id via the resource table.
   *
   * @param resourceId string of resource id
   * @return optional UUID of workspace id
   */
  @ReadTransaction
  public Optional<UUID> getWorkspaceIdFromResourceId(String resourceId) {
    final String sql = "SELECT workspace_id FROM resource WHERE resource_id = :resource_id";
    MapSqlParameterSource params = new MapSqlParameterSource().addValue("resource_id", resourceId);
    try {
      return Optional.ofNullable(
          DataAccessUtils.singleResult(
              jdbcTemplate.query(
                  sql, params, (rs, rowNum) -> UUID.fromString(rs.getString("workspace_id")))));
    } catch (EmptyResultDataAccessException e) {
      return Optional.empty();
    }
  }

  /**
   * grantRole - adds the user to the poc_user table if it doesn't exist; validates matching user
   * info if it does exist. - inserts the entry in the poc_grant table. It is not an error to run
   * this twice. We log that the workspace is there, but consider that a success.
   *
   * @param workspaceId workspace the grant is for
   * @param role the Iam role to grant
   * @param pocUser info about the user
   */
  @WriteTransaction
  public void grantRole(UUID workspaceId, WsmIamRole role, PocUser pocUser) {
    // Make sure the user is saved in the user table
    addUser(pocUser);
    String userId = pocUser.getUserId();

    // Grant the owner role on the workspace to the user
    final String sql =
        "INSERT INTO poc_grant (workspace_id, iam_role, user_id)"
            + "VALUES (:workspace_id, :iam_role, :user_id)";
    MapSqlParameterSource params =
        new MapSqlParameterSource()
            .addValue("workspace_id", workspaceId.toString())
            .addValue("iam_role", role.toSamRole())
            .addValue("user_id", userId);

    try {
      jdbcTemplate.update(sql, params);
      logger.info(
          "Granted role {} to user {} for workspace {}", role.toSamRole(), userId, workspaceId);
    } catch (DuplicateKeyException e) {
      logger.info(
          "Grant already exists for role {} to user {} for workspace {}",
          role.toSamRole(),
          userId,
          workspaceId);
    }
  }

  @ReadTransaction
  public List<UUID> listAccessible(PocUser pocUser) {
    final String sql = "SELECT DISTINCT workspace_id FROM poc_grant" + " WHERE user_id = :user_id ";
    MapSqlParameterSource params =
        new MapSqlParameterSource().addValue("user_id", pocUser.getUserId());
    return jdbcTemplate.query(
        sql,
        params,
        (rs, rowNum) -> {
          return UUID.fromString(rs.getString("workspace_id"));
        });
  }

  /**
   * Revoke a role from a user on a workspace. Note that we do not do any special checks for owner.
   * It is possible to remove the last owner on a workspace. Don't do that.
   *
   * @param workspaceId workspace the grant is for
   * @param role the Iam role to revoke
   * @param userId user id to revoke
   */
  @WriteTransaction
  public void revokeRole(UUID workspaceId, WsmIamRole role, String userId) {

    // Grant the owner role on the workspace to the user
    final String sql =
        "DELETE FROM poc_grant"
            + " WHERE workspace_id = :workspace_id AND iam_role = :iam_role AND user_id = :user_id";
    MapSqlParameterSource params =
        new MapSqlParameterSource()
            .addValue("workspace_id", workspaceId.toString())
            .addValue("iam_role", role.toSamRole())
            .addValue("user_id", userId);

    int rowsAffected = jdbcTemplate.update(sql, params);
    logger.info(
        "{} role {} from user {} for workspace {}",
        (rowsAffected > 0) ? "Revoked " : "No grant found for ",
        role.toSamRole(),
        userId,
        workspaceId);
  }

  /**
   * @param workspaceId workspace the grant is for
   * @param roles the Iam roles to check
   * @param userId user id to check
   * @return true if the user has the role; false otherwise
   */
  @ReadTransaction
  public boolean roleCheck(UUID workspaceId, List<WsmIamRole> roles, String userId) {
    return roleCheckWorker(workspaceId, roles, userId);
  }

  /**
   * @param workspaceId workspace the grant is for
   * @param role the Iam role to check
   * @param userId user id to check
   * @return true if the user has the role; false otherwise
   */
  @ReadTransaction
  public boolean roleCheck(UUID workspaceId, WsmIamRole role, String userId) {
    return roleCheckWorker(workspaceId, List.of(role), userId);
  }

  private boolean roleCheckWorker(UUID workspaceId, List<WsmIamRole> roles, String userId) {
    final String sql =
        "SELECT COUNT(*) FROM poc_grant"
            + " WHERE workspace_id = :workspace_id AND user_id = :user_id AND iam_role IN (:iam_roles)";

    MapSqlParameterSource params =
        new MapSqlParameterSource()
            .addValue("workspace_id", workspaceId.toString())
            .addValue(
                "iam_roles", roles.stream().map(WsmIamRole::toSamRole).collect(Collectors.toList()))
            .addValue("user_id", userId);

    Integer count = jdbcTemplate.queryForObject(sql, params, Integer.class);
    return (count != null && count > 0);
  }

  public void addUser(PocUser pocUser) {
    Optional<PocUser> optionalPocUser = getUser(pocUser.getUserId());
    if (optionalPocUser.isPresent()) {
      if ((StringUtils.equalsIgnoreCase(optionalPocUser.get().email, pocUser.getEmail()))) {
        return; // everything matches up
      }
      throw new IllegalStateException("Mismatched email for user");
    }

    // We did not find the user, so insert them
    final String sql = "INSERT INTO poc_user (user_id, email) VALUES (:user_id, :email)";

    MapSqlParameterSource params =
        new MapSqlParameterSource()
            .addValue("user_id", pocUser.getUserId())
            .addValue("email", pocUser.getEmail());

    jdbcTemplate.update(sql, params);
  }

  // Return the emails of users with the requested role
  public List<String> listRoleUsers(UUID workspaceId, WsmIamRole role) {
    final String sql =
        "SELECT poc_user.email FROM poc_user INNER JOIN poc_grant USING (user_id)"
            + " WHERE poc_grant.workspace_id = :workspace_id"
            + " AND poc_grant.iam_role = :role";

    MapSqlParameterSource params =
        new MapSqlParameterSource()
            .addValue("workspace_id", workspaceId.toString())
            .addValue("role", role.toSamRole());

    return jdbcTemplate.query(sql, params, (rs, rowNum) -> rs.getString("email"));
  }

  public Optional<PocUser> getUserFromEmail(String email) {
    final String sql = "SELECT user_id, email FROM poc_user WHERE email = :email";
    MapSqlParameterSource params = new MapSqlParameterSource().addValue("email", email);
    return getUserWorker(sql, params);
  }

  private Optional<PocUser> getUser(String userId) {
    final String sql = "SELECT user_id, email FROM poc_user WHERE user_id = :user_id";
    MapSqlParameterSource params = new MapSqlParameterSource().addValue("user_id", userId);
    return getUserWorker(sql, params);
  }

  private Optional<PocUser> getUserWorker(String sql, MapSqlParameterSource params) {
    try {
      return Optional.ofNullable(
          DataAccessUtils.singleResult(
              jdbcTemplate.query(
                  sql,
                  params,
                  (rs, rowNum) -> {
                    return new PocUser()
                        .userId(rs.getString("user_id"))
                        .email(rs.getString("email"));
                  })));
    } catch (EmptyResultDataAccessException e) {
      return Optional.empty();
    }
  }

  private void grantRole(UUID workspaceId, WsmIamRole role, String userId) {
    final String sql =
        "INSERT INTO poc_grant (workspace_id, iam_role, user_id)"
            + "VALUES (:workspace_id, :iam_role, :user_id)";
    MapSqlParameterSource params =
        new MapSqlParameterSource()
            .addValue("workspace_id", workspaceId.toString())
            .addValue("iam_role", role.toSamRole())
            .addValue("user_id", userId);

    try {
      jdbcTemplate.update(sql, params);
      logger.info(
          "Granted role {} to user {} for workspace {}", role.toSamRole(), userId, workspaceId);
    } catch (DuplicateKeyException e) {
      logger.info(
          "Grant already exists for role {} to user {} for workspace {}",
          role.toSamRole(),
          userId,
          workspaceId);
    }
  }

  // Class for passing around user info
  public static class PocUser {
    private String userId;
    private String email;

    public PocUser() {}

    public PocUser(AuthenticatedUserRequest userRequest) {
      this.userId = userRequest.getSubjectId();
      this.email = userRequest.getEmail();
    }

    public String getUserId() {
      return userId;
    }

    public PocUser userId(String userId) {
      this.userId = userId;
      return this;
    }

    public String getEmail() {
      return email;
    }

    public PocUser email(String email) {
      this.email = email;
      return this;
    }
  }
}
