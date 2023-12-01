package bio.terra.workspace.azureDatabaseUtils.validation;

import java.util.List;
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

  public void validateOidFormat(String userOid) {
    UUID.fromString(Objects.requireNonNull(userOid, "userOid must be specified"));
  }

  public void validatePgDumpCommand(List<String> commandList) {
    var commandPath = commandList.get(0);
    if (!commandPath.equals("pg_dump")) {
      throw new IllegalArgumentException("commandPath must be `pg_dump`");
    }
    if (commandList.size() != 14) {
      throw new IllegalArgumentException(
          "pg_dump command list must contain exactly 14 arguments and values");
    }

    // What other validations should be used?

  }

  public void validatePgRestoreCommand(List<String> commandList) {
    var commandPath = commandList.get(0);
    if (!commandPath.equals("psql")) {
      throw new IllegalArgumentException("commandPath must be `psql`");
    }
    if (commandList.size() != 11) {
      throw new IllegalArgumentException(
          "psql command list must contain exactly 11 arguments and values");
    }

    // What other validations should be used?
  }
}
