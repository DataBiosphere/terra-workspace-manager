package bio.terra.workspace.azureDatabaseUtils.create;

import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class CreateDatabaseService {
  private static final Logger logger = LoggerFactory.getLogger(CreateDatabaseService.class);
  private final CreateDatabaseDao createDatabaseDao;

  @Autowired
  public CreateDatabaseService(CreateDatabaseDao createDatabaseDao) {
    this.createDatabaseDao = createDatabaseDao;
  }

  public void createDatabase(String newDbName, String newDbUserName, String newDbUserOid) {
    validateDatabaseNameFormat(newDbName);
    validateRoleNameFormat(newDbUserName);
    validateUserOidFormat(newDbUserOid);

    logger.info(
        "Creating database {} with user {} and OID {}", newDbName, newDbUserName, newDbUserOid);

    createDatabaseDao.createDatabase(newDbName);
    createDatabaseDao.createRole(newDbUserName, newDbUserOid);
    createDatabaseDao.grantAllPrivileges(newDbUserName, newDbName);
    createDatabaseDao.revokeAllPublicPrivileges(newDbName);
  }

  private void validateDatabaseNameFormat(String databaseName) {
    if (databaseName != null && !databaseName.matches("(?i)^[a-z][a-z0-9_]*$")) {
      throw new IllegalArgumentException(
          "Database name must be specified and start with a letter and contain only letters, numbers, and underscores");
    }
  }

  private void validateRoleNameFormat(String roleName) {
    if (roleName != null && !roleName.matches("(?i)^[a-z][a-z0-9_-]*$")) {
      throw new IllegalArgumentException(
          "Role name must be specified and start with a letter and contain only letters, numbers, dashes, and underscores");
    }
  }

  private void validateUserOidFormat(String userOid) {
    UUID.fromString(Objects.requireNonNull(userOid, "userOid must be specified"));
  }
}
