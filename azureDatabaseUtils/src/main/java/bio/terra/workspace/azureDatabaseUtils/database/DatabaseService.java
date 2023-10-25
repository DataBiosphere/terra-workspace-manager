package bio.terra.workspace.azureDatabaseUtils.database;

import bio.terra.workspace.azureDatabaseUtils.process.LocalProcessLauncher;
import bio.terra.workspace.azureDatabaseUtils.storage.BlobStorage;
import bio.terra.workspace.azureDatabaseUtils.validation.Validator;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;

import com.azure.identity.extensions.jdbc.postgresql.AzurePostgresqlAuthenticationPlugin;
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
  public DatabaseService(
      DatabaseDao databaseDao, Validator validator, BlobStorage blobStorage) {
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
      String sourceDbName,
      String sourceDbHost,
      String sourceDbPort,
      String sourceDbUser,
      String pgDumpFilename,
      String destinationWorkspaceId,
      String blobstorageDetails) {
    logger.info("running DatabaseService.pgDump against {}", sourceDbName);
    logger.info("destinationWorkspaceId: {}", destinationWorkspaceId);
    try {
      databaseDao.grantRole(sourceDbUser, sourceDbName);
      
      List<String> commandList =
          generateCommandList("pg_dump", sourceDbName, sourceDbHost, sourceDbPort, sourceDbUser);
      Map<String, String> envVars = Map.of("PGPASSWORD", determinePassword());
      LocalProcessLauncher localProcessLauncher = new LocalProcessLauncher();
      localProcessLauncher.launchProcess(commandList, envVars);

      storage.streamOutputToBlobStorage(
          localProcessLauncher.getInputStream(), pgDumpFilename, destinationWorkspaceId, blobstorageDetails);

      String output = checkForError(localProcessLauncher);
      logger.info("pg_dump output: {}", output);

    } catch (PSQLException ex) {
      logger.error("process error: {}", ex.getMessage());
    }
  }

  public List<String> generateCommandList(
      String pgDumpPath, String sourceDbName, String dbHost, String dbPort, String dbUser) {
    Map<String, String> command = new LinkedHashMap<>();

    command.put(pgDumpPath, null);
    command.put("-b", null);
    command.put("-h", dbHost);
    command.put("-p", dbPort);
    command.put("-U", dbUser);
    command.put("-d", sourceDbName);

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

  private String checkForError(LocalProcessLauncher localProcessLauncher) {
    // materialize only the first 1024 bytes of the error stream to ensure we don't DoS ourselves
    int errorLimit = 1024;

    int exitCode = localProcessLauncher.waitForTerminate();
    if (exitCode != 0) {
      InputStream errorStream =
          localProcessLauncher.getOutputForProcess(LocalProcessLauncher.Output.ERROR);
      try {
        String error = new String(errorStream.readNBytes(errorLimit)).trim();
        logger.error("process error: {}", error);
        return error;
      } catch (IOException e) {
        logger.warn(
            "process failed with exit code {}, but encountered an exception reading the error output: {}",
            exitCode,
            e.getMessage());
        return "Unknown error";
      }
    }
    return "";
  }

  private String determinePassword() throws PSQLException {
    return new String(
        new AzurePostgresqlAuthenticationPlugin(new Properties())
            .getPassword(AuthenticationRequestType.CLEARTEXT_PASSWORD));
  }
}
