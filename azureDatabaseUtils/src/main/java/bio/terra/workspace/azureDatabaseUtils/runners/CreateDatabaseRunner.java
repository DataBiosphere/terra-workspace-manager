package bio.terra.workspace.azureDatabaseUtils.runners;

import bio.terra.workspace.azureDatabaseUtils.database.DatabaseService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Profile("CreateDatabase")
@Component
// TODO: remove with https://broadworkbench.atlassian.net/browse/WOR-1165
public class CreateDatabaseRunner implements ApplicationRunner {
  @Value("${env.params.newDbUserOid}")
  private String newDbUserOid;

  @Value("${env.params.newDbUserName}")
  private String newDbUserName;

  @Value("${env.params.newDbName}")
  private String newDbName;

  private final DatabaseService databaseService;

  public CreateDatabaseRunner(DatabaseService databaseService) {
    this.databaseService = databaseService;
  }

  @Override
  public void run(ApplicationArguments args) {
    databaseService.createDatabaseWithManagedIdentity(newDbName, newDbUserName, newDbUserOid);
  }
}
