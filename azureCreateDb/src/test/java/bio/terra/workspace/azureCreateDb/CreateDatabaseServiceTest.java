package bio.terra.workspace.azureCreateDb;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

public class CreateDatabaseServiceTest extends BaseUnitTest {
  @Autowired
  private CreateDatabaseService createDatabaseService;

  @MockBean
  private CreateDatabaseDao createDatabaseDao;

  @Test
  void testCreateDatabase() {
    final String newDbName = "testCreateDatabase";
    final String newDbUserName = "testCreateRole";
    final String newDbUserOid = UUID.randomUUID().toString();

    createDatabaseService.createDatabase(newDbName, newDbUserName, newDbUserOid);

    verify(createDatabaseDao).createDatabase(newDbName);
    verify(createDatabaseDao).createRole(newDbUserName, newDbUserOid);
    verify(createDatabaseDao).grantAllPrivileges(newDbUserName, newDbName);
    verify(createDatabaseDao).revokeAllPublicPrivileges(newDbName);
  }

  @Test
  void testDatabaseNameValidation() {
    final String newDbName = "testCreateDatabase; DROP DATABASE testCreateDatabase";
    final String newDbUserName = "testCreateRole";
    final String newDbUserOid = UUID.randomUUID().toString();
    assertThrows(IllegalArgumentException.class, () -> createDatabaseService.createDatabase(newDbName, newDbUserName, newDbUserOid));
  }

  @Test
  void testRoleNameValidation() {
    final String newDbName = "testCreateDatabase";
    final String newDbUserName = "testCreateRole; DROP ROLE testCreateRole";
    final String newDbUserOid = UUID.randomUUID().toString();
    assertThrows(IllegalArgumentException.class, () -> createDatabaseService.createDatabase(newDbName, newDbUserName, newDbUserOid));
  }

  @Test
  void testOidValidation() {
    final String newDbName = "testCreateDatabase";
    final String newDbUserName = "testCreateRole";
    final String newDbUserOid = "not a uuid";
    assertThrows(IllegalArgumentException.class, () -> createDatabaseService.createDatabase(newDbName, newDbUserName, newDbUserOid));
  }
}
