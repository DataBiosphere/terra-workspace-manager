package bio.terra.workspace.azureDatabaseUtils.database;

import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class DatabaseDao {
  private final NamedParameterJdbcTemplate jdbcTemplate;

  @Autowired
  public DatabaseDao(NamedParameterJdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  /**
   * Create a database with the given name
   *
   * @param databaseName
   * @return true if the database was created, false if it already existed
   */
  public boolean createDatabase(String databaseName) {
    // databaseName should already be validated by the service layer
    // CREATE DATABASE does not like bind parameters
    try {
      jdbcTemplate.update("CREATE DATABASE " + databaseName, Map.of());
      return true;
    } catch (BadSqlGrammarException e) {
      // ignore if the database already exists
      if (!e.getSQLException().getMessage().contains("already exists")) {
        throw e;
      } else {
        return false;
      }
    }
  }

  /**
   * Create a role with the given name and associated with an object id of a manged identity. See
   * https://learn.microsoft.com/en-us/azure/postgresql/flexible-server/how-to-manage-azure-ad-users#create-a-role-using-azure-ad-object-identifier
   *
   * @param roleName typically the same as the managed identity name
   * @param userOID object id of the managed identity, also known as the principal id
   * @return the text `exists` if the role already exists, or the output of
   *     `pgaadauth_create_principal_with_oid` if the role was created
   */
  public String createRoleForManagedIdentity(String roleName, String userOID) {
    MapSqlParameterSource params =
        new MapSqlParameterSource().addValue("roleName", roleName).addValue("userOID", userOID);
    return jdbcTemplate
        .query(
            """
                SELECT case
                when exists(select * FROM pg_roles where rolname=:roleName) then 'exists'
                else pgaadauth_create_principal_with_oid(:roleName, :userOID, 'service', false, false)
                end""",
            params,
            (rs, rowNum) -> rs.getString(1))
        .get(0);
  }

  /**
   * Create a role in the database with the given name.
   *
   * @param roleName
   * @return true if the role was created, false if the role already existed
   */
  public boolean createRole(String roleName) {
    // roleName should already be validated by the service layer
    try {
      jdbcTemplate.update(
          """
              CREATE ROLE "%s"
              """
              .formatted(roleName),
          Map.of());
      return true;
    } catch (BadSqlGrammarException e) {
      // ignore if the role already exists
      if (!e.getSQLException().getMessage().contains("already exists")) {
        throw e;
      }
      return false;
    }
  }

  /**
   * Delete a role in the database with the given name.
   *
   * @param roleName
   * @return true if the role was deleted, false if the role did not exist
   */
  public boolean deleteRole(String roleName) {
    // roleName should already be validated by the service layer
    try {
      jdbcTemplate.update(
          """
              DROP ROLE "%s"
              """
              .formatted(roleName),
          Map.of());
      return true;
    } catch (BadSqlGrammarException e) {
      // ignore if the role already deleted
      if (!e.getSQLException().getMessage().contains("does not exist")) {
        throw e;
      }
      return false;
    }
  }

  public void grantRole(String roleName, String targetRoleName) {
    // roleNames should already be validated by the service layer
    // GRANT does not like bind parameters
    jdbcTemplate.update(
        """
        GRANT "%s" TO "%s"
        """
            .formatted(targetRoleName, roleName),
        Map.of());
  }

  public void reassignOwner(String roleName, String targetRoleName) {
    jdbcTemplate.update(
        """
        REASSIGN OWNED BY "%s" TO "%s"
        """
            .formatted(roleName, targetRoleName),
        Map.of());
  }

  public void grantAllPrivileges(String roleName, String databaseName) {
    // databaseName should already be validated by the service layer
    // roleName should already be validated by the service layer
    // GRANT does not like bind parameters
    jdbcTemplate.update(
        """
        GRANT ALL PRIVILEGES ON DATABASE %s TO "%s"
        """
            .formatted(databaseName, roleName),
        Map.of());
  }

  public void revokeAllPublicPrivileges(String databaseName) {
    // databaseName should already be validated by the service layer
    // REVOKE does not like bind parameters
    jdbcTemplate.update(
        "REVOKE ALL PRIVILEGES ON DATABASE %s FROM PUBLIC".formatted(databaseName), Map.of());
  }

  public void revokeLoginPrivileges(String roleName) {
    // roleName should already be validated by the service layer
    // REVOKE does not like bind parameters
    jdbcTemplate.update(
        """
        ALTER ROLE "%s" NOLOGIN
        """
            .formatted(roleName),
        Map.of());
  }

  public void restoreLoginPrivileges(String roleName) {
    // roleName should already be validated by the service layer
    // REVOKE does not like bind parameters
    jdbcTemplate.update(
        """
        ALTER ROLE "%s" LOGIN
        """
            .formatted(roleName),
        Map.of());
  }

  /**
   * Terminate all sessions for the given role.
   *
   * @param roleName
   * @return the number of sessions terminated
   */
  public int terminateSessionsForRole(String roleName) {
    // roleName should already be validated by the service layer
    return jdbcTemplate
        .query(
            """
        SELECT count(pg_terminate_backend(pg_stat_activity.pid))
        FROM pg_stat_activity
        WHERE pg_stat_activity.usename = :roleName;
        """,
            Map.of("roleName", roleName),
            (rs, rowNum) -> rs.getInt(1))
        .get(0);
  }

  public String getCurrentDatabaseName() {
    return jdbcTemplate
        .query("SELECT current_database()", Map.of(), (rs, rowNum) -> rs.getString(1))
        .get(0);
  }
}
