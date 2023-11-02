package bio.terra.workspace.azureDatabaseUtils.database;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;

import bio.terra.workspace.azureDatabaseUtils.BaseUnitTest;
import bio.terra.workspace.azureDatabaseUtils.storage.BlobStorage;
import bio.terra.workspace.azureDatabaseUtils.validation.Validator;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

public class DatabaseServiceTest extends BaseUnitTest {
  @Autowired private DatabaseService databaseService;

  @MockBean private DatabaseDao databaseDao;
  @MockBean private Validator validator;
  @MockBean private BlobStorage storage;

  @Test
  void testCreateDatabaseWithDbRole() {
    final String newDbName = "testCreateDatabase";

    databaseService.createDatabaseWithDbRole(newDbName);

    verify(databaseDao).createDatabase(newDbName);
    verify(databaseDao).createRole(newDbName);
    verify(databaseDao).grantAllPrivileges(newDbName, newDbName);
    verify(databaseDao).revokeAllPublicPrivileges(newDbName);

    verify(validator).validateDatabaseNameFormat(newDbName);
  }

  @Test
  void testRevokeNamespaceRoleAccess() {
    final String namespaceRole = "testRevokeNamespaceRoleAccess";

    databaseService.revokeNamespaceRoleAccess(namespaceRole);

    var inOrder = inOrder(databaseDao);
    // order is important, reversing it can lead to connections sneaking in
    inOrder.verify(databaseDao).grantRole("postgres", "pg_signal_backend");
    inOrder.verify(databaseDao).revokeLoginPrivileges(namespaceRole);
    inOrder.verify(databaseDao).terminateSessionsForRole(namespaceRole);

    verify(validator).validateRoleNameFormat(namespaceRole);

    // make sure calling it again does not cause an error
    databaseService.revokeNamespaceRoleAccess(namespaceRole);
  }

  @Test
  void testRestoreNamespaceRoleAccess() {
    final String namespaceRole = "testRestoreNamespaceRoleAccess";

    databaseService.restoreNamespaceRoleAccess(namespaceRole);

    verify(databaseDao).restoreLoginPrivileges(namespaceRole);
    verify(validator).validateRoleNameFormat(namespaceRole);
  }

  @Test
  void testGenerateCommandList() {
    List<String> pgDumpCommandList =
        databaseService.generateCommandList(
            "/test/pg_dump", "testdb", "http://host.org", "5432", "testuser");
    String pgDumpCommand = String.join(" ", pgDumpCommandList);
    assertThat(
        pgDumpCommand,
        equalTo(
            "/test/pg_dump -b --no-privileges --no-owner -h http://host.org -p 5432 -U testuser -d testdb -v -w"));

    List<String> pgRestoreCommandList =
        databaseService.generateCommandList(
            "/test/psql", "testdb", "http://host.org", "5432", "testuser");
    String pgRestoreCommand = String.join(" ", pgRestoreCommandList);
    assertThat(
        pgRestoreCommand,
        equalTo("/test/psql -h http://host.org -p 5432 -U testuser -d testdb -v -w"));
  }
}
