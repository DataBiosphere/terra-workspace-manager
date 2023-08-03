package bio.terra.workspace.azureDatabaseUtils.runners;

import bio.terra.workspace.azureDatabaseUtils.database.DatabaseService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Profile("DeleteUser")
@Component
public class DeleteUserRunner implements ApplicationRunner {
  @Value("${env.params.dbUserName}")
  private String dbUserName;

  private final DatabaseService databaseService;

  public DeleteUserRunner(DatabaseService databaseService) {
    this.databaseService = databaseService;
  }

  @Override
  public void run(ApplicationArguments args) {
    databaseService.deleteLoginRole(dbUserName);
  }
}
