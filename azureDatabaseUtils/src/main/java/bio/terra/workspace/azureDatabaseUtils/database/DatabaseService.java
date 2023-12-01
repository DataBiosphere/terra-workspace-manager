package bio.terra.workspace.azureDatabaseUtils.database;

import bio.terra.workspace.azureDatabaseUtils.process.LaunchProcessException;
import bio.terra.workspace.azureDatabaseUtils.process.LocalProcessLauncher;
import bio.terra.workspace.azureDatabaseUtils.storage.BlobStorage;
import bio.terra.workspace.azureDatabaseUtils.validation.Validator;
import com.azure.identity.extensions.jdbc.postgresql.AzurePostgresqlAuthenticationPlugin;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
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

  private SecretKey decodeBase64Key(String encryptionKeyBase64) {
    byte[] decodedKey = Base64.getDecoder().decode(encryptionKeyBase64);
    return new SecretKeySpec(decodedKey, 0, decodedKey.length, "AES");
  }

  private OutputStream encryptIntoOutputStream(OutputStream origin, String encryptionKeyBase64)
      throws NoSuchPaddingException, NoSuchAlgorithmException, InvalidKeyException,
          InvalidAlgorithmParameterException {
    SecretKey encryptionKey = decodeBase64Key(encryptionKeyBase64);
    Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
    c.init(Cipher.ENCRYPT_MODE, encryptionKey, new GCMParameterSpec(128, c.getIV()));
    return new CipherOutputStream(origin, c);
  }

  private InputStream decryptFromInputStream(InputStream origin, String encryptionKeyBase64)
      throws NoSuchPaddingException, NoSuchAlgorithmException, InvalidKeyException,
          InvalidAlgorithmParameterException {
    SecretKey encryptionKey = decodeBase64Key(encryptionKeyBase64);
    Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
    c.init(Cipher.DECRYPT_MODE, encryptionKey, new GCMParameterSpec(128, c.getIV()));
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
      throws PSQLException, NoSuchPaddingException, NoSuchAlgorithmException, InvalidKeyException,
          IOException, InvalidAlgorithmParameterException {

    // Grant the database role (dbName) to the landing zone identity (adminUser).
    // In theory, we should be revoking this role after the operation is complete.
    // We are choosing to *not* revoke this role for now, because:
    // (1) we could run into concurrency issues if multiple users attempt to clone the same
    // workspace at once;
    // (2) the workspace identity can grant itself access at any time, so revoking the role
    // doesn't protect us.
    databaseDao.grantRole(adminUser, dbName);

    List<String> commandList = generateCommandList("pg_dump", dbName, dbHost, dbPort, adminUser);
    Map<String, String> envVars = Map.of("PGPASSWORD", determinePassword());

    logger.info(
        "Streaming DatabaseService.pgDump output into blob file {} in container: {}",
        blobFileName,
        blobContainerName);

    localProcessLauncher.launchProcess(commandList, envVars);

    // Stream wrapping is confusing, so in English: local process output -> encryption -> base64
    // encoding -> blob storage
    try (OutputStream blobOutputStream =
        storage.getBlobStorageUploadOutputStream(
            blobFileName, blobContainerName, blobContainerUrlAuthenticated)) {
      try (OutputStream encoderWrappedOutputStream = Base64.getEncoder().wrap(blobOutputStream)) {
        try (OutputStream encryptedBlobOutputStream =
            encryptIntoOutputStream(encoderWrappedOutputStream, encryptionKeyBase64)) {
          localProcessLauncher.getInputStream().transferTo(encryptedBlobOutputStream);
          encryptedBlobOutputStream.flush();
        }
      }
    }

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
      throws PSQLException, NoSuchPaddingException, NoSuchAlgorithmException, InvalidKeyException,
          IOException, InvalidAlgorithmParameterException {

    // Grant the database role (dbName) to the workspace identity (adminUser).
    // In theory, we should be revoking this role after the operation is complete.
    // We are choosing to *not* revoke this role for now, because:
    // (1) we could run into concurrency issues if multiple users attempt to clone the same
    // workspace at once;
    // (2) the workspace identity can grant itself access at any time, so revoking the role
    // doesn't protect us.

    databaseDao.grantRole(adminUser, dbName);

    List<String> commandList = generateCommandList("psql", dbName, dbHost, dbPort, adminUser);
    Map<String, String> envVars = Map.of("PGPASSWORD", determinePassword());

    logger.info(
        "Streaming DatabaseService.pgRestore input from blob file {} in container: {}",
        blobFileName,
        blobContainerName);

    localProcessLauncher.launchProcess(commandList, envVars);

    // Stream wrapping is confusing, so in English: blob storage > base 64 decoding > decryption >
    // local process input
    // NB we use the pipes to receive blob storage via an output stream and convert that into an
    // input stream for the subsequent processing
    // NB we use a separate thread to handle the download so that we can be downloading and sending
    // to the thread in parallel
    try (PipedOutputStream blobStorageReceiver = new PipedOutputStream();
        PipedInputStream blobStorageReplayer = new PipedInputStream(blobStorageReceiver, 2048)) {
      try (InputStream decoderWrappedInputStream = Base64.getDecoder().wrap(blobStorageReplayer)) {
        try (InputStream decryptedBlobInputStream =
            decryptFromInputStream(decoderWrappedInputStream, encryptionKeyBase64)) {
          Runnable downloadThread =
              () ->
                  storage.streamInputFromBlobStorage(
                      blobStorageReceiver,
                      blobFileName,
                      blobContainerName,
                      blobContainerUrlAuthenticated);
          Thread downloadThreadInstance = new Thread(downloadThread);
          downloadThreadInstance.start();

          decryptedBlobInputStream.transferTo(localProcessLauncher.getOutputStream());
        }
      }
    }
    localProcessLauncher.getOutputStream().flush();

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

  protected String determinePassword() throws PSQLException {
    return new String(
        new AzurePostgresqlAuthenticationPlugin(new Properties())
            .getPassword(AuthenticationRequestType.CLEARTEXT_PASSWORD));
  }
}
