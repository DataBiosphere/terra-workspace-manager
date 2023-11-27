package bio.terra.workspace.azureDatabaseUtils.database;

import bio.terra.workspace.azureDatabaseUtils.process.LaunchProcessException;
import bio.terra.workspace.azureDatabaseUtils.process.LocalProcessLauncher;
import bio.terra.workspace.azureDatabaseUtils.storage.BlobStorage;
import bio.terra.workspace.azureDatabaseUtils.validation.Validator;
import com.azure.identity.extensions.jdbc.postgresql.AzurePostgresqlAuthenticationPlugin;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import javax.crypto.*;
import javax.crypto.spec.SecretKeySpec;
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

  private SecretKey decodeBase64EncryptionKey(String encryptionKeyBase64) {
    byte[] decodedKey = Base64.getDecoder().decode(encryptionKeyBase64);
    return new SecretKeySpec(decodedKey, 0, decodedKey.length, "AES");
  }

  private SecretKey decodeBase64Key(String encryptionKeyBase64) {
    byte[] decodedKey = Base64.getDecoder().decode(encryptionKeyBase64);
    return new SecretKeySpec(decodedKey, 0, decodedKey.length, "AES");
  }

  private InputStream encryptStream(InputStream origin, String encryptionKeyBase64) throws NoSuchPaddingException, NoSuchAlgorithmException, InvalidKeyException {
    SecretKey encryptionKey = decodeBase64Key(encryptionKeyBase64);
    Cipher c = Cipher.getInstance("AES");
    c.init(Cipher.ENCRYPT_MODE, encryptionKey);
    return new CipherInputStream(origin, c);
  }

  private InputStream decryptStream(InputStream origin, String encryptionKeyBase64) throws NoSuchPaddingException, NoSuchAlgorithmException, InvalidKeyException {
    SecretKey encryptionKey = decodeBase64Key(encryptionKeyBase64);
    Cipher c = Cipher.getInstance("AES");
    c.init(Cipher.DECRYPT_MODE, encryptionKey);
    return new CipherInputStream(origin, c);
  }


  public void pgDump(
      String dbName,
      String dbHost,
      String dbPort,
      String adminUser,
      String blobFileName,
      String blobContainerName,
      String blobContainerUrlAuthenticated,
      String encryptionKeyBase64,
      LocalProcessLauncher localProcessLauncher)
          throws PSQLException, NoSuchPaddingException, NoSuchAlgorithmException, InvalidKeyException, IOException {

    // Grant the database role (dbName) to the landing zone identity (adminUser).
    // In theory, we should be revoking this role after the operation is complete.
    // We are choosing to *not* revoke this role for now, because:
    // (1) we could run into concurrency issues if multiple users attempt to clone the same
    // workspace at once;
    // (2) the workspace identity can grant itself access at any time, so revoking the role
    // doesn't protect us.
    databaseDao.grantRole(adminUser, dbName);

    List<String> commandList = generateCommandList("pg_dump", dbName, dbHost, dbPort, adminUser);
    Map<String, String> envVars = Map.of("PGPASSWORD", "FIXME"); // determinePassword()

    logger.info(
        "Streaming DatabaseService.pgDump output into blob file {} in container: {} with key: {}",
        blobFileName,
        blobContainerName,
        encryptionKeyBase64);

    localProcessLauncher.launchProcess(commandList, envVars);

    byte[] processOutput = localProcessLauncher.getInputStream().readAllBytes();
    String base64Output = Base64.getEncoder().encodeToString(processOutput);
    logger.info("Dump output bytes: {}", base64Output.substring(0, Math.min(base64Output.length(), 50)));

    byte[] encryptedProcessOutput = encryptStream(new ByteArrayInputStream(processOutput), encryptionKeyBase64).readAllBytes();
    String base64EncodedOutput = Base64.getEncoder().encodeToString(encryptedProcessOutput);
    logger.info("Encoded output bytes: {}", base64EncodedOutput.substring(0, Math.min(base64EncodedOutput.length(), 50)));

    storage.streamOutputToBlobStorage(
        new ByteArrayInputStream(base64EncodedOutput.getBytes(StandardCharsets.UTF_8)),
        blobFileName,
        blobContainerName,
        blobContainerUrlAuthenticated);

    logger.info("Stream to blob storage complete");

    checkForError(localProcessLauncher);
  }

  public void pgRestore(
      String dbName,
      String dbHost,
      String dbPort,
      String adminUser,
      String blobFileName,
      String blobContainerName,
      String blobContainerUrlAuthenticated,
      String encryptionKeyBase64,
      LocalProcessLauncher localProcessLauncher)
          throws PSQLException, NoSuchPaddingException, NoSuchAlgorithmException, InvalidKeyException, IOException {

    // Grant the database role (dbName) to the workspace identity (adminUser).
    // In theory, we should be revoking this role after the operation is complete.
    // We are choosing to *not* revoke this role for now, because:
    // (1) we could run into concurrency issues if multiple users attempt to clone the same
    // workspace at once;
    // (2) the workspace identity can grant itself access at any time, so revoking the role
    // doesn't protect us.

    databaseDao.grantRole(adminUser, dbName);

    List<String> commandList = generateCommandList("psql", dbName, dbHost, dbPort, adminUser);
    Map<String, String> envVars = Map.of("PGPASSWORD", "FIXME"); // determinePassword());

    localProcessLauncher.launchProcess(commandList, envVars);

    logger.info(
        "Running DatabaseService.pgRestore on file {} from blob container: {} with key: {}",
        blobFileName,
        blobContainerName,
        encryptionKeyBase64);

    ByteArrayOutputStream fileDownloadStream = new ByteArrayOutputStream();
    storage.streamInputFromBlobStorage(fileDownloadStream, blobFileName, blobContainerName, blobContainerUrlAuthenticated);
    String downloadedFileBase64 = fileDownloadStream.toString(StandardCharsets.UTF_8);
    logger.info("Downloaded file bytes: {}", downloadedFileBase64.substring(0, Math.min(downloadedFileBase64.length(), 50)));

    byte[] base64DecodedFile = Base64.getDecoder().decode(downloadedFileBase64);

    ByteArrayInputStream downloadedFileInputStream = new ByteArrayInputStream(base64DecodedFile);
    byte[] decryptedFileBytes = decryptStream(downloadedFileInputStream, encryptionKeyBase64).readAllBytes();
    String decryptedFileBytesString = new String(decryptedFileBytes, StandardCharsets.UTF_8);
    logger.info("Decrypted file bytes: {}", decryptedFileBytesString.substring(0, Math.min(decryptedFileBytesString.length(), 50)));

    localProcessLauncher.getOutputStream().write(decryptedFileBytes);
    checkForError(localProcessLauncher);

    logger.info("Stream to local process complete");

    databaseDao.reassignOwner(adminUser, dbName);
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
    final int errorLimit = 1024;

    int exitCode = localProcessLauncher.waitForTerminate();
    if (exitCode != 0) {
      InputStream errorStream =
          localProcessLauncher.getOutputForProcess(LocalProcessLauncher.Output.ERROR);

      String errorMsg;
      try {
        errorMsg =
            "process error: "
                + new String(errorStream.readNBytes(errorLimit), StandardCharsets.UTF_8).trim();
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

//  @Test
//  void testPgDump() throws Exception {
//
//    DatabaseDao databaseDao = mock(DatabaseDao.class);
//    Validator validator = mock(Validator.class);
//    BlobStorage storage = mock(BlobStorage.class);
//    LocalProcessLauncher localProcessLauncher = mock(LocalProcessLauncher.class);
//
//    DatabaseService testDatabaseService = new DatabaseService(databaseDao, validator, storage);
//
//
//    doNothing().when(databaseDao).grantRole(any(), any());
//    doNothing().when(localProcessLauncher).launchProcess(any(), any());
//
//    ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream("MYTESTINPUTCONTENT".getBytes());
//    when(localProcessLauncher.getInputStream()).thenReturn(byteArrayInputStream);
//
//    ArgumentCaptor<InputStream> inputStreamArgumentCaptor = ArgumentCaptor.forClass(InputStream.class);
//    doNothing().when(storage).streamOutputToBlobStorage(inputStreamArgumentCaptor.capture(), any(), any(), any());
//
//    testDatabaseService.pgDump(
//            "testdb",
//            "http://host.org",
//            "5432",
//            "testuser",
//            "testfile",
//            "testcontainer",
//            "http://host.org",
//            "AAA",
//            localProcessLauncher);
//
//    byte[] bytes = inputStreamArgumentCaptor.getValue().readAllBytes();
//    String output = Base64.getEncoder().encodeToString(bytes);
//    assertThat(output, equalTo("MYTESTINPUTCONTENT"));
//
//  }
}
