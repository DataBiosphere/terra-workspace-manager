package bio.terra.workspace.azureDatabaseUtils.validation;

import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class Validator {
  public void validateDatabaseNameFormat(String databaseName) {
    if (databaseName == null || !databaseName.matches("(?i)^[a-z][a-z0-9_]*$")) {
      throw new IllegalArgumentException(
          "Database name must be specified and start with a letter and contain only letters, numbers, and underscores");
    }
  }

  public void validateRoleNameFormat(String roleName) {
    if (roleName == null || !roleName.matches("(?i)^[a-z][a-z0-9_-]*$")) {
      throw new IllegalArgumentException(
          "Role name must be specified and start with a letter and contain only letters, numbers, dashes, and underscores");
    }
  }

  public void validateUserOidFormat(String userOid) {
    UUID.fromString(Objects.requireNonNull(userOid, "userOid must be specified"));
  }
}
