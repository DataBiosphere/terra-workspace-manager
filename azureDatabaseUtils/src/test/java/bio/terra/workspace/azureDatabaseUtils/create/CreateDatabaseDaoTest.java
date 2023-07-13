package bio.terra.workspace.azureDatabaseUtils.create;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

import bio.terra.workspace.azureDatabaseUtils.BaseUnitTest;
import java.sql.Types;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

public class CreateDatabaseDaoTest extends BaseUnitTest {

  @Autowired private CreateDatabaseDao createDatabaseDao;

  @Autowired private JdbcTemplate jdbcTemplate;

  private final String testDatabaseName = "testCreateDatabase";
  private final String testRoleName = "testCreateRole";

  @BeforeEach
  @AfterEach
  void cleanup() {
    jdbcTemplate.execute("DROP DATABASE IF EXISTS " + testDatabaseName);
    jdbcTemplate.execute("DROP ROLE IF EXISTS \"%s\"".formatted(testRoleName));
  }

  @Test
  void testCreateDatabase() {
    createDatabaseDao.createDatabase(testDatabaseName);

    jdbcTemplate
        .query(
            "select count(*) from pg_database where datname = lower(?)",
            new Object[] {testDatabaseName},
            new int[] {Types.VARCHAR},
            (rs, rowNum) -> rs.getInt(1))
        .forEach(count -> assertThat(count, equalTo(1)));
  }

  @Test
  void testCreateRole() {
    final String testUserOID = "test";
    createRoleFunction();
    assertThat(createDatabaseDao.createRole(testRoleName, testUserOID), equalTo("created"));
    assertThat(createDatabaseDao.createRole(testRoleName, testUserOID), equalTo("exists"));
  }

  @Test
  void testGrantAllPrivileges() {
    jdbcTemplate.execute("CREATE DATABASE " + testDatabaseName);
    createTestRole(testRoleName);
    createDatabaseDao.grantAllPrivileges(testRoleName, testDatabaseName);
  }

  @Test
  void testRevokeAllPublicPrivileges() {
    jdbcTemplate.execute("CREATE DATABASE " + testDatabaseName);
    createDatabaseDao.revokeAllPublicPrivileges(testDatabaseName);
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
}
