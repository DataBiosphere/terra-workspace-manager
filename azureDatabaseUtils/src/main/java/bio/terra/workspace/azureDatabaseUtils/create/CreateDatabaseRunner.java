package bio.terra.workspace.azureDatabaseUtils.create;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Profile("CreateDatabase")
@Component
// TODO: remove with https://broadworkbench.atlassian.net/browse/WOR-1165
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
    createDatabaseService.createDatabaseWithManagedIdentity(newDbName, newDbUserName, newDbUserOid);
  }
}
