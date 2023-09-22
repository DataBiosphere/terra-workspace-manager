package bio.terra.workspace.azureDatabaseUtils.runners;

import bio.terra.workspace.azureDatabaseUtils.database.DatabaseService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Profile("RestoreNamespaceRoleAccess")
@Component
public class RestoreNamespaceRoleAccessRunner implements ApplicationRunner {
  @Value("${env.params.namespaceRole}")
  private String namespaceRole;

  private final DatabaseService databaseService;

  public RestoreNamespaceRoleAccessRunner(DatabaseService databaseService) {
    this.databaseService = databaseService;
  }

  @Override
  public void run(ApplicationArguments args) {
    databaseService.restoreNamespaceRoleAccess(namespaceRole);
  }
}
