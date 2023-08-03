package bio.terra.workspace.azureDatabaseUtils.create;

import static org.mockito.Mockito.verify;

import bio.terra.workspace.azureDatabaseUtils.BaseUnitTest;
import bio.terra.workspace.azureDatabaseUtils.validation.Validator;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

public class CreateDatabaseServiceTest extends BaseUnitTest {
  @Autowired private CreateDatabaseService createDatabaseService;

  @MockBean private CreateDatabaseDao createDatabaseDao;
  @MockBean private Validator validator;

  @Test
  // TODO: remove with https://broadworkbench.atlassian.net/browse/WOR-1165
  void testCreateDatabaseWithManagedIdentity() {
    final String newDbName = "testCreateDatabase";
    final String newDbUserName = "testCreateRole";
    final String newDbUserOid = UUID.randomUUID().toString();

    createDatabaseService.createDatabaseWithManagedIdentity(newDbName, newDbUserName, newDbUserOid);

    verify(createDatabaseDao).createDatabase(newDbName);
    verify(createDatabaseDao).createRoleForManagedIdentity(newDbUserName, newDbUserOid);
    verify(createDatabaseDao).grantAllPrivileges(newDbUserName, newDbName);
    verify(createDatabaseDao).revokeAllPublicPrivileges(newDbName);

    verify(validator).validateDatabaseNameFormat(newDbName);
    verify(validator).validateRoleNameFormat(newDbUserName);
    verify(validator).validateUserOidFormat(newDbUserOid);
  }

  @Test
  void testCreateDatabaseWithDbRole() {
    final String newDbName = "testCreateDatabase";

    createDatabaseService.createDatabaseWithDbRole(newDbName);

    verify(createDatabaseDao).createDatabase(newDbName);
    verify(createDatabaseDao).createRole(newDbName);
    verify(createDatabaseDao).grantAllPrivileges(newDbName, newDbName);
    verify(createDatabaseDao).revokeAllPublicPrivileges(newDbName);

    verify(validator).validateDatabaseNameFormat(newDbName);
  }
}
