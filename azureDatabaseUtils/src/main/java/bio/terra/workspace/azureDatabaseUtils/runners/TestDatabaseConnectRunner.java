package bio.terra.workspace.azureDatabaseUtils.runners;

import bio.terra.workspace.azureDatabaseUtils.database.DatabaseDao;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Profile("TestDatabaseConnect")
@Component
public class TestDatabaseConnectRunner implements ApplicationRunner {
  @Value("${env.db.connectToDatabase}")
  private String expectedDatabaseName;

  private final DatabaseDao databaseDao;

  public TestDatabaseConnectRunner(DatabaseDao databaseDao) {
    this.databaseDao = databaseDao;
  }

  @Override
  public void run(ApplicationArguments args) {
    var currentDatabaseName = databaseDao.getCurrentDatabaseName();
    if (!currentDatabaseName.equalsIgnoreCase(expectedDatabaseName)) {
      throw new RuntimeException(
          String.format(
              "Expected to be connected to database %s, but was connected to %s",
              expectedDatabaseName, currentDatabaseName));
    }
  }
}
