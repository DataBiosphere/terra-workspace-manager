package bio.terra.workspace.azureDatabaseUtils.database;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

import bio.terra.workspace.azureDatabaseUtils.BaseUnitTest;
import java.sql.Types;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

public class DatabaseDaoTest extends BaseUnitTest {

  @Autowired private DatabaseDao databaseDao;

  @Autowired private JdbcTemplate jdbcTemplate;

  private final String testDatabaseName = "testCreateDatabase";
  private final String testRoleName = "test-Create-Role";
  private final String testRoleAdminName = "test-Admin-Role";

  @BeforeEach
  @AfterEach
  void cleanup() {
    jdbcTemplate.execute("DROP DATABASE IF EXISTS " + testDatabaseName);
    jdbcTemplate.execute("DROP ROLE IF EXISTS \"%s\"".formatted(testRoleName));
    jdbcTemplate.execute("DROP ROLE IF EXISTS \"%s\"".formatted(testRoleAdminName));
  }

  @Test
  void testCreateDatabase() {
    assertThat(databaseDao.createDatabase(testDatabaseName), equalTo(true));

    jdbcTemplate
        .query(
            "select count(*) from pg_database where datname = lower(?)",
            new Object[] {testDatabaseName},
            new int[] {Types.VARCHAR},
            (rs, rowNum) -> rs.getInt(1))
        .forEach(count -> assertThat(count, equalTo(1)));

    // verify that we can call it again without error
    assertThat(databaseDao.createDatabase(testDatabaseName), equalTo(false));
  }

  @Test
  void testCreateRoleForManagedIdentity() {
    final String testUserOID = "test";
    createRoleFunction();
    assertThat(
        databaseDao.createRoleForManagedIdentity(testRoleName, testUserOID), equalTo("created"));
    assertThat(
        databaseDao.createRoleForManagedIdentity(testRoleName, testUserOID), equalTo("exists"));
  }

  @Test
  void testCreateRole() {
    assertThat(databaseDao.createRole(testRoleName), equalTo(true));
    assertThat(databaseDao.createRole(testRoleName), equalTo(false));
  }

  @Test
  void testDeleteRole() {
    assertThat(databaseDao.createRole(testRoleName), equalTo(true));

    assertThat(databaseDao.deleteRole(testRoleName), equalTo(true));
    assertThat(databaseDao.deleteRole(testRoleName), equalTo(false));
  }

  void testGrantRole() {
    assertThat(databaseDao.createRole(testRoleName), equalTo(true));
    final String otherRole = testRoleName + "2";
    try {
      assertThat(databaseDao.createRole(otherRole), equalTo(true));

      databaseDao.grantRole(testRoleName, otherRole);
    } finally {
      databaseDao.deleteRole(otherRole);
    }
  }

  @Test
  void testGrantAllPrivileges() {
    jdbcTemplate.execute("CREATE DATABASE " + testDatabaseName);
    createTestRole(testRoleName);
    databaseDao.grantAllPrivileges(testRoleName, testDatabaseName);
  }

  @Test
  void testRevokeAllPublicPrivileges() {
    jdbcTemplate.execute("CREATE DATABASE " + testDatabaseName);
    databaseDao.revokeAllPublicPrivileges(testDatabaseName);
  }

  @Test
  void testRestoreAndRevokeLoginPrivileges() {
    createTestRole(testRoleName);
    assertThat(roleCanLogin(testRoleName), equalTo(false));

    databaseDao.restoreLoginPrivileges(testRoleName);
    assertThat(roleCanLogin(testRoleName), equalTo(true));

    databaseDao.revokeLoginPrivileges(testRoleName);
    assertThat(roleCanLogin(testRoleName), equalTo(false));
  }

  @Test
  void testTerminateSessionsForRole() {
    createTestRole(testRoleName);
    assertThat(databaseDao.terminateSessionsForRole(testRoleName), equalTo(0));
  }

  @Test
  void testReassignOwner() {
    jdbcTemplate.execute("CREATE DATABASE " + testDatabaseName);
    createTestRole(testRoleName);
    createTestRole(testRoleAdminName);
    databaseDao.reassignOwnerForCbasDatabase(testRoleAdminName);
  }

  private void createTestRole(String testRoleName) {
    jdbcTemplate.execute("CREATE ROLE \"%s\"".formatted(testRoleName));
  }

  /**
   * Creates a function the is expected to exist in an Azure Postgres database. The real thing does
   * more than just creates a role.
   */
  private void createRoleFunction() {
    var function =
        """
            create or replace function pgaadauth_create_principal_with_oid(rolename text, oid text, foo text, bar boolean, baz boolean)
            returns text
            language plpgsql
            as
            $$
            declare
            begin
                EXECUTE 'create role "' || rolename || '"';
                return 'created';
            end;
            $$;""";
    jdbcTemplate.execute(function);
  }

  private boolean roleCanLogin(String roleName) {
    return jdbcTemplate
        .query(
            "select rolcanlogin from pg_roles where rolname = ?",
            new Object[] {roleName},
            new int[] {Types.VARCHAR},
            (rs, rowNum) -> rs.getBoolean(1))
        .get(0);
  }
}
