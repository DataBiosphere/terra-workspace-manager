package bio.terra.workspace.azureDatabaseUtils.database;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;

import bio.terra.workspace.azureDatabaseUtils.BaseUnitTest;
import bio.terra.workspace.azureDatabaseUtils.validation.Validator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

public class DatabaseServiceTest extends BaseUnitTest {
  @Autowired private DatabaseService databaseService;

  @MockBean private DatabaseDao databaseDao;
  @MockBean private Validator validator;

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
}
