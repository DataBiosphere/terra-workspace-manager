package bio.terra.workspace.azureDatabaseUtils.database;

import static org.mockito.Mockito.verify;

import bio.terra.workspace.azureDatabaseUtils.BaseUnitTest;
import bio.terra.workspace.azureDatabaseUtils.validation.Validator;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

public class DatabaseServiceTest extends BaseUnitTest {
  @Autowired private DatabaseService databaseService;

  @MockBean private DatabaseDao databaseDao;
  @MockBean private Validator validator;

  @Test
  // TODO: remove with https://broadworkbench.atlassian.net/browse/WOR-1165
  void testCreateDatabaseWithManagedIdentity() {
    final String newDbName = "testCreateDatabase";
    final String newDbUserName = "testCreateRole";
    final String newDbUserOid = UUID.randomUUID().toString();

    databaseService.createDatabaseWithManagedIdentity(newDbName, newDbUserName, newDbUserOid);

    verify(databaseDao).createDatabase(newDbName);
    verify(databaseDao).createRoleForManagedIdentity(newDbUserName, newDbUserOid);
    verify(databaseDao).grantAllPrivileges(newDbUserName, newDbName);
    verify(databaseDao).revokeAllPublicPrivileges(newDbName);

    verify(validator).validateDatabaseNameFormat(newDbName);
    verify(validator).validateRoleNameFormat(newDbUserName);
    verify(validator).validateOidFormat(newDbUserOid);
  }

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
}
