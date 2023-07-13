package bio.terra.workspace.azureDatabaseUtils.create;

import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class CreateDatabaseDao {
  private final NamedParameterJdbcTemplate jdbcTemplate;

  @Autowired
  public CreateDatabaseDao(NamedParameterJdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public void createDatabase(String databaseName) {
    // databaseName should already be validated by the service layer
    // CREATE DATABASE does not like bind parameters
    jdbcTemplate.update("CREATE DATABASE " + databaseName, Map.of());
  }

  /**
   * Create a role with the given name and associated with an object id of a manged identity.
   * See https://learn.microsoft.com/en-us/azure/postgresql/flexible-server/how-to-manage-azure-ad-users#create-a-role-using-azure-ad-object-identifier
   * @param roleName typically the same as the managed identity name
   * @param userOID object id of the managed identity, also known as the principal id
   * @return the text `exists` if the role already exists, or the output of `pgaadauth_create_principal_with_oid` if the role was created
   */
  public String createRole(String roleName, String userOID) {
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

  public void grantAllPrivileges(String roleName, String databaseName) {
    // databaseName should already be validated by the service layer
    // roleName should already be validated by the service layer
    // GRANT does not like bind parameters
    jdbcTemplate.update(
        "GRANT ALL PRIVILEGES ON DATABASE %s TO \"%s\"".formatted(databaseName, roleName),
        Map.of());
  }

  public void revokeAllPublicPrivileges(String databaseName) {
    // databaseName should already be validated by the service layer
    // REVOKE does not like bind parameters
    jdbcTemplate.update(
        "REVOKE ALL PRIVILEGES ON DATABASE %s FROM PUBLIC".formatted(databaseName), Map.of());
  }
}
