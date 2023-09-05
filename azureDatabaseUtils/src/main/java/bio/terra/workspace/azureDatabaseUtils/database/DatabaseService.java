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
    validator.validateOidFormat(newDbUserOid);

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
    databaseDao.createRole(newDbName); // create a role with the same name as the database
    databaseDao.grantAllPrivileges(newDbName, newDbName); // db name and role name are the same
    databaseDao.revokeAllPublicPrivileges(newDbName);
  }

  public void createNamespaceRole(
      String namespaceRole, String managedIdentityOid, Set<String> databaseNames) {
    validator.validateRoleNameFormat(namespaceRole);
    validator.validateOidFormat(managedIdentityOid);
    databaseNames.forEach(validator::validateDatabaseNameFormat);

    logger.info(
        "Creating namespace role {} with OID {} for databases {}",
        namespaceRole,
        managedIdentityOid,
        databaseNames);

    databaseDao.createRoleForManagedIdentity(namespaceRole, managedIdentityOid);
    databaseNames.forEach(databaseName -> databaseDao.grantRole(namespaceRole, databaseName));
  }

  public void deleteNamespaceRole(String namespaceRole) {
    validator.validateRoleNameFormat(namespaceRole);

    logger.info("Deleting namespace role {}", namespaceRole);

    databaseDao.deleteRole(namespaceRole);
  }
}