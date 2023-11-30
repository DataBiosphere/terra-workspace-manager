package bio.terra.workspace.azureDatabaseUtils.runners;

import bio.terra.workspace.azureDatabaseUtils.database.DatabaseService;
import bio.terra.workspace.azureDatabaseUtils.process.LocalProcessLauncher;
import org.postgresql.util.PSQLException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import javax.crypto.NoSuchPaddingException;
import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

@Profile("PgDumpDatabase")
@Component
public class PgDumpDatabaseRunner implements ApplicationRunner {

  @Value("${env.db.connectToDatabase}")
  private String dbName;

  @Value("${env.db.url}")
  private String dbHost;

  @Value("${env.db.port}")
  private String dbPort;

  @Value("${env.db.user}")
  private String dbUser;

  @Value("${env.params.blobFileName}")
  private String blobFileName;

  @Value("${env.params.blobContainerName}")
  private String blobContainerName;

  @Value("${env.params.blobContainerUrlAuthenticated}")
  private String blobContainerUrlAuthenticated;

  @Value("${env.params.encryptionKey}")
  private String encryptionKey;

  private final DatabaseService databaseService;

  public PgDumpDatabaseRunner(DatabaseService databaseService) {
    this.databaseService = databaseService;
  }

  @Override
  public void run(ApplicationArguments args) throws PSQLException, NoSuchPaddingException, NoSuchAlgorithmException, InvalidKeyException, IOException {
    LocalProcessLauncher localProcessLauncher = new LocalProcessLauncher();
    databaseService.pgDump(
        dbName,
        dbHost,
        dbPort,
        dbUser,
        blobFileName,
        blobContainerName,
        blobContainerUrlAuthenticated,
        encryptionKey,
        localProcessLauncher);
  }
}
