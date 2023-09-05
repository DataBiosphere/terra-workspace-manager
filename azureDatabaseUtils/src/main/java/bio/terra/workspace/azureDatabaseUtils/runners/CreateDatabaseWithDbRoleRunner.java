package bio.terra.workspace.azureDatabaseUtils.runners;

import bio.terra.workspace.azureDatabaseUtils.database.DatabaseService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Profile("CreateDatabaseWithDbRole")
@Component
public class CreateDatabaseWithDbRoleRunner implements ApplicationRunner {
  @Value("${env.params.newDbName}")
  private String newDbName;

  private final DatabaseService databaseService;

  public CreateDatabaseWithDbRoleRunner(DatabaseService databaseService) {
    this.databaseService = databaseService;
  }

  @Override
  public void run(ApplicationArguments args) {
    databaseService.createDatabaseWithDbRole(newDbName);
  }
}
