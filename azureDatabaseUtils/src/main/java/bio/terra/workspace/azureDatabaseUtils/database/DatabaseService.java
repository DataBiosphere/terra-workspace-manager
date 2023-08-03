package bio.terra.workspace.azureDatabaseUtils.database;

import bio.terra.workspace.azureDatabaseUtils.validation.Validator;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class DatabaseService {
  private static final Logger logger = LoggerFactory.getLogger(DatabaseService.class);
  private final DatabaseDao databaseDao;
  private final Validator validator;

  @Autowired
  public DatabaseService(DatabaseDao databaseDao, Validator validator) {
    this.databaseDao = databaseDao;
    this.validator = validator;
  }

  // TODO: remove with https://broadworkbench.atlassian.net/browse/WOR-1165
  public void createDatabaseWithManagedIdentity(
      String newDbName, String newDbUserName, String newDbUserOid) {
    validator.validateDatabaseNameFormat(newDbName);
    validator.validateRoleNameFormat(newDbUserName);
    validator.validateUserOidFormat(newDbUserOid);

    logger.info(
        "Creating database {} with user {} and OID {}", newDbName, newDbUserName, newDbUserOid);

    databaseDao.createDatabase(newDbName);
    databaseDao.createRoleForManagedIdentity(newDbUserName, newDbUserOid);
    databaseDao.grantAllPrivileges(newDbUserName, newDbName);
    databaseDao.revokeAllPublicPrivileges(newDbName);
  }

  public void createDatabaseWithDbRole(String newDbName) {
    validator.validateDatabaseNameFormat(newDbName);

    logger.info("Creating database {} with db role of same name", newDbName);

    databaseDao.createDatabase(newDbName);
    databaseDao.createRole(newDbName);
    databaseDao.grantAllPrivileges(newDbName, newDbName);
    databaseDao.revokeAllPublicPrivileges(newDbName);
  }

  public void createLoginRole(
      String newDbUserName, String newDbUserOid, Set<String> databaseNames) {
    validator.validateRoleNameFormat(newDbUserName);
    validator.validateUserOidFormat(newDbUserOid);
    databaseNames.forEach(validator::validateDatabaseNameFormat);

    logger.info(
        "Creating login role {} with OID {} for databases {}",
        newDbUserName,
        newDbUserOid,
        databaseNames);

    databaseDao.createRoleForManagedIdentity(newDbUserName, newDbUserOid);
    databaseNames.forEach(databaseName -> databaseDao.grantRole(newDbUserName, databaseName));
  }

  public void deleteLoginRole(String newDbUserName) {
    validator.validateRoleNameFormat(newDbUserName);

    logger.info("Deleting login role {}", newDbUserName);

    databaseDao.deleteRole(newDbUserName);
  }
}
