package bio.terra.workspace.azureDatabaseUtils.validation;

import static org.junit.jupiter.api.Assertions.assertThrows;

import bio.terra.workspace.azureDatabaseUtils.BaseUnitTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class ValidatorTest extends BaseUnitTest {
  @Autowired private Validator validator;

  @Test
  void testDatabaseNameValidation() {
    final String newDbName = "testCreateDatabase; DROP DATABASE testCreateDatabase";
    assertThrows(
        IllegalArgumentException.class, () -> validator.validateDatabaseNameFormat(newDbName));
  }

  @Test
  void testMissingDatabaseNameValidation() {
    assertThrows(IllegalArgumentException.class, () -> validator.validateDatabaseNameFormat(null));
  }

  @Test
  void testRoleNameValidation() {
    final String newDbUserName = "testCreateRole; DROP ROLE testCreateRole";
    assertThrows(
        IllegalArgumentException.class, () -> validator.validateRoleNameFormat(newDbUserName));
  }

  @Test
  void testMissingRoleNameValidation() {
    assertThrows(IllegalArgumentException.class, () -> validator.validateRoleNameFormat(null));
  }

  @Test
  void testOidValidation() {
    final String newDbUserOid = "not a uuid";
    assertThrows(IllegalArgumentException.class, () -> validator.validateOidFormat(newDbUserOid));
  }

  @Test
  void testMissingOidValidation() {
    assertThrows(NullPointerException.class, () -> validator.validateOidFormat(null));
  }
}
