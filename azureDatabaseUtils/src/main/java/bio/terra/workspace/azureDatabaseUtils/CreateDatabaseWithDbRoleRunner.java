package bio.terra.workspace.azureDatabaseUtils;

import bio.terra.workspace.azureDatabaseUtils.create.CreateDatabaseService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Profile("CreateDatabaseWithDbRole")
@Component
public class CreateDatabaseWithDbRoleRunner implements ApplicationRunner {
  @Value("${azureDatabaseUtils.create.newDbName}")
  private String newDbName;

  private final CreateDatabaseService createDatabaseService;

  public CreateDatabaseWithDbRoleRunner(CreateDatabaseService createDatabaseService) {
    this.createDatabaseService = createDatabaseService;
  }

  @Override
  public void run(ApplicationArguments args) {
    createDatabaseService.createDatabaseWithDbRole(newDbName);
  }
}
