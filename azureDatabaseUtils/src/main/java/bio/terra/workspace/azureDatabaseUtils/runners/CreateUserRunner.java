package bio.terra.workspace.azureDatabaseUtils.runners;

import bio.terra.workspace.azureDatabaseUtils.database.DatabaseService;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Profile("CreateUser")
@Component
public class CreateUserRunner implements ApplicationRunner {
  @Value("${env.params.newDbUserOid}")
  private String newDbUserOid;

  @Value("${env.params.newDbUserName}")
  private String newDbUserName;

  @Value("${env.params.databaseNames}")
  private String databaseNames;

  private final DatabaseService databaseService;

  public CreateUserRunner(DatabaseService databaseService) {
    this.databaseService = databaseService;
  }

  @Override
  public void run(ApplicationArguments args) {
    databaseService.createLoginRole(newDbUserName, newDbUserOid, Set.of(databaseNames.split(",")));
  }
}
