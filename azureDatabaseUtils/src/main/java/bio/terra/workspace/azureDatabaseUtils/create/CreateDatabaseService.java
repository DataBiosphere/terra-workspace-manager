package bio.terra.workspace.azureDatabaseUtils.create;

import bio.terra.workspace.azureDatabaseUtils.validation.Validator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class CreateDatabaseService {
  private static final Logger logger = LoggerFactory.getLogger(CreateDatabaseService.class);
  private final CreateDatabaseDao createDatabaseDao;
  private final Validator validator;

  @Autowired
  public CreateDatabaseService(CreateDatabaseDao createDatabaseDao, Validator validator) {
    this.createDatabaseDao = createDatabaseDao;
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

    createDatabaseDao.createDatabase(newDbName);
    createDatabaseDao.createRoleForManagedIdentity(newDbUserName, newDbUserOid);
    createDatabaseDao.grantAllPrivileges(newDbUserName, newDbName);
    createDatabaseDao.revokeAllPublicPrivileges(newDbName);
  }

  public void createDatabaseWithDbRole(String newDbName) {
    validator.validateDatabaseNameFormat(newDbName);

    logger.info("Creating database {} with db role of same name", newDbName);

    createDatabaseDao.createDatabase(newDbName);
    createDatabaseDao.createRole(newDbName);
    createDatabaseDao.grantAllPrivileges(newDbName, newDbName);
    createDatabaseDao.revokeAllPublicPrivileges(newDbName);
  }
}
