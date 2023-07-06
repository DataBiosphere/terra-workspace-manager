package bio.terra.workspace.azureDatabaseUtils.create;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Profile("CreateDatabase")
@Component
public class CreateDatabaseRunner implements ApplicationRunner {
  @Value("${azureDatabaseUtils.create.newDbUserOid}")
  private String newDbUserOid;

  @Value("${azureDatabaseUtils.create.newDbUserName}")
  private String newDbUserName;

  @Value("${azureDatabaseUtils.create.newDbName}")
  private String newDbName;

  private final CreateDatabaseService createDatabaseService;

  public CreateDatabaseRunner(CreateDatabaseService createDatabaseService) {
    this.createDatabaseService = createDatabaseService;
  }

  @Override
  public void run(ApplicationArguments args) {
    createDatabaseService.createDatabase(newDbName, newDbUserName, newDbUserOid);
  }
}
