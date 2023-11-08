package bio.terra.workspace.azureDatabaseUtils.runners;

import bio.terra.workspace.azureDatabaseUtils.database.DatabaseService;
import bio.terra.workspace.azureDatabaseUtils.process.LocalProcessLauncher;
import org.postgresql.util.PSQLException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Profile("PgRestoreDatabase")
@Component
public class PgRestoreDatabaseRunner implements ApplicationRunner {

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

  private final DatabaseService databaseService;

  public PgRestoreDatabaseRunner(DatabaseService databaseService) {
    this.databaseService = databaseService;
  }

  @Override
  public void run(ApplicationArguments args) throws PSQLException {
    LocalProcessLauncher localProcessLauncher = new LocalProcessLauncher();
    databaseService.pgRestore(
        dbName,
        dbHost,
        dbPort,
        dbUser,
        blobFileName,
        blobContainerName,
        blobContainerUrlAuthenticated,
        localProcessLauncher);
  }
}
