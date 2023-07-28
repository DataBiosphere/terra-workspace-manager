package bio.terra.workspace.azureDatabaseUtils.create;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;

import bio.terra.workspace.azureDatabaseUtils.BaseUnitTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

public class CreateDatabaseServiceTest extends BaseUnitTest {
  @Autowired private CreateDatabaseService createDatabaseService;

  @MockBean private CreateDatabaseDao createDatabaseDao;

  @Test
  void testCreateDatabase() {
    final String newDbName = "testCreateDatabase";
    final String newDbUserName = "testCreateRole";
    final String newDbUserOid = UUID.randomUUID().toString();

    createDatabaseService.createDatabaseWithManagedIdentity(newDbName, newDbUserName, newDbUserOid);

    verify(createDatabaseDao).createDatabase(newDbName);
    verify(createDatabaseDao).createRoleForManagedIdentity(newDbUserName, newDbUserOid);
    verify(createDatabaseDao).grantAllPrivileges(newDbUserName, newDbName);
    verify(createDatabaseDao).revokeAllPublicPrivileges(newDbName);
  }

  @Test
  void testDatabaseNameValidation() {
    final String newDbName = "testCreateDatabase; DROP DATABASE testCreateDatabase";
    final String newDbUserName = "testCreateRole";
    final String newDbUserOid = UUID.randomUUID().toString();
    assertThrows(
        IllegalArgumentException.class,
        () -> createDatabaseService.createDatabaseWithManagedIdentity(newDbName, newDbUserName, newDbUserOid));
  }

  @Test
  void testRoleNameValidation() {
    final String newDbName = "testCreateDatabase";
    final String newDbUserName = "testCreateRole; DROP ROLE testCreateRole";
    final String newDbUserOid = UUID.randomUUID().toString();
    assertThrows(
        IllegalArgumentException.class,
        () -> createDatabaseService.createDatabaseWithManagedIdentity(newDbName, newDbUserName, newDbUserOid));
  }

  @Test
  void testOidValidation() {
    final String newDbName = "testCreateDatabase";
    final String newDbUserName = "testCreateRole";
    final String newDbUserOid = "not a uuid";
    assertThrows(
        IllegalArgumentException.class,
        () -> createDatabaseService.createDatabaseWithManagedIdentity(newDbName, newDbUserName, newDbUserOid));
  }
}
