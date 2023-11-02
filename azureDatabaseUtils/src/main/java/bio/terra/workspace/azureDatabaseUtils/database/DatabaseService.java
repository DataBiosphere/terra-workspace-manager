package bio.terra.workspace.azureDatabaseUtils.database;

import bio.terra.workspace.azureDatabaseUtils.process.LaunchProcessException;
import bio.terra.workspace.azureDatabaseUtils.process.LocalProcessLauncher;
import bio.terra.workspace.azureDatabaseUtils.storage.BlobStorage;
import bio.terra.workspace.azureDatabaseUtils.validation.Validator;
import com.azure.identity.extensions.jdbc.postgresql.AzurePostgresqlAuthenticationPlugin;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import org.postgresql.plugin.AuthenticationRequestType;
import org.postgresql.util.PSQLException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class DatabaseService {
  private static final Logger logger = LoggerFactory.getLogger(DatabaseService.class);
  private final DatabaseDao databaseDao;
  private final Validator validator;
  private final BlobStorage storage;

  @Value("${spring.datasource.username}")
  private String datasourceUserName;

  @Autowired
  public DatabaseService(DatabaseDao databaseDao, Validator validator, BlobStorage blobStorage) {
    this.databaseDao = databaseDao;
    this.validator = validator;
    this.storage = blobStorage;
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

  public void revokeNamespaceRoleAccess(String namespaceRole) {
    validator.validateRoleNameFormat(namespaceRole);

    logger.info("Revoking namespace role access {}", namespaceRole);

    // grant pg_signal_backend to the datasource user, so we can terminate sessions
    // this only needs to be done once per database server,
    // but it's idempotent, so we do it every time
    databaseDao.grantRole(datasourceUserName, "pg_signal_backend");

    // revoke first ensures no new connections are made, so we can be sure we terminate all sessions
    databaseDao.revokeLoginPrivileges(namespaceRole);
    databaseDao.terminateSessionsForRole(namespaceRole);
  }

  public void restoreNamespaceRoleAccess(String namespaceRole) {
    validator.validateRoleNameFormat(namespaceRole);

    logger.info("Restoring namespace role access {}", namespaceRole);

    databaseDao.restoreLoginPrivileges(namespaceRole);
  }

  public void pgDump(
      String dbName,
      String dbHost,
      String dbPort,
      String adminUser,
      String blobFileName,
      String blobContainerName,
      String blobContainerUrlAuthenticated) {

    // Grant the database role (dbName) to the landing zone identity (adminUser).
    // In theory, we should be revoking this role after the operation is complete.
    // We are choosing to *not* revoke this role for now, because:
    // (1) we could run into concurrency issues if multiple users attempt to clone the same
    // workspace at once;
    // (2) the workspace identity can grant itself access at any time, so revoking the role
    // doesn't protect us.
    databaseDao.grantRole(adminUser, dbName);

    List<String> commandList = generateCommandList("pg_dump", dbName, dbHost, dbPort, adminUser);
    Map<String, String> envVars = null;
    try {
      envVars = Map.of("PGPASSWORD", determinePassword());
    } catch (PSQLException e) {
      logger.error(e.getMessage());
    }

    logger.info("running DatabaseService.pgDump: {}", String.join(" ", commandList));
    logger.info("blobContainerName: {}", blobContainerName);

    LocalProcessLauncher localProcessLauncher = new LocalProcessLauncher();
    localProcessLauncher.launchProcess(commandList, envVars);

    storage.streamOutputToBlobStorage(
        localProcessLauncher.getInputStream(),
        blobFileName,
        blobContainerName,
        blobContainerUrlAuthenticated);

    checkForError(localProcessLauncher);
  }

  public void pgRestore(
      String dbName,
      String dbHost,
      String dbPort,
      String adminUser,
      String blobFileName,
      String blobContainerName,
      String blobContainerUrlAuthenticated)
      throws PSQLException {

    boolean doCleanup = false;

    // Grant the database role (dbName) to the workspace identity (adminUser).
    // In theory, we should be revoking this role after the operation is complete.
    // We are choosing to *not* revoke this role for now, because:
    // (1) we could run into concurrency issues if multiple users attempt to clone the same
    // workspace at once;
    // (2) the workspace identity can grant itself access at any time, so revoking the role
    // doesn't protect us.
    try {
      databaseDao.grantRole(adminUser, dbName);

      List<String> commandList = generateCommandList("psql", dbName, dbHost, dbPort, adminUser);
      Map<String, String> envVars = null;
      envVars = Map.of("PGPASSWORD", determinePassword());
      LocalProcessLauncher localProcessLauncher = new LocalProcessLauncher();
      localProcessLauncher.launchProcess(commandList, envVars);

      logger.info("running DatabaseService.pgRestore: {}", String.join(" ", commandList));
      logger.info("blobContainerName: {}", blobContainerName);

      storage.streamInputFromBlobStorage(
          localProcessLauncher.getOutputStream(),
          blobFileName,
          blobContainerName,
          blobContainerUrlAuthenticated);

      // doCleanup isn't set to true until checkForError completes without throwing an exception
      checkForError(localProcessLauncher);
      databaseDao.reassignOwner(adminUser, dbName);
      doCleanup = true;
    } finally {
      if (doCleanup) {
        storage.deleteBlob(blobFileName, blobContainerName, blobContainerUrlAuthenticated);
      }
    }
  }

  public List<String> generateCommandList(
      String pgCommandPath, String dbName, String dbHost, String dbPort, String dbUser) {
    Map<String, String> command = new LinkedHashMap<>();

    command.put(pgCommandPath, null);
    if (pgCommandPath.contains("pg_dump")) {
      command.put("-b", null);
      command.put("--no-privileges", null);
      command.put("--no-owner", null);
    }

    command.put("-h", dbHost);
    command.put("-p", dbPort);
    command.put("-U", dbUser);
    command.put("-d", dbName);

    List<String> commandList = new ArrayList<>();
    for (Map.Entry<String, String> entry : command.entrySet()) {
      commandList.add(entry.getKey());
      if (entry.getValue() != null) {
        commandList.add(entry.getValue());
      }
    }
    commandList.add("-v");
    commandList.add("-w");

    return commandList;
  }

  private void checkForError(LocalProcessLauncher localProcessLauncher)
      throws LaunchProcessException {
    // materialize only the first 1024 bytes of the error stream to ensure we don't DoS ourselves
    int errorLimit = 1024000;

    int exitCode = localProcessLauncher.waitForTerminate();
    if (exitCode != 0) {
      InputStream errorStream =
          localProcessLauncher.getOutputForProcess(LocalProcessLauncher.Output.ERROR);

      String errorMsg;
      try {
        String error = new String(errorStream.readNBytes(errorLimit)).trim();
        errorMsg = "process error: " + error;
      } catch (IOException e) {
        errorMsg =
            "process failed with exit code "
                + exitCode
                + ", but encountered an exception reading the error output: "
                + e.getMessage();
      }
      throw new LaunchProcessException(errorMsg);
    }
  }

  private String determinePassword() throws PSQLException {
    return new String(
        new AzurePostgresqlAuthenticationPlugin(new Properties())
            .getPassword(AuthenticationRequestType.CLEARTEXT_PASSWORD));
  }
}
