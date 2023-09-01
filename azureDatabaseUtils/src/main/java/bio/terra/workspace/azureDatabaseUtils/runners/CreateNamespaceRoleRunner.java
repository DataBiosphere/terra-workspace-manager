package bio.terra.workspace.azureDatabaseUtils.runners;

import bio.terra.workspace.azureDatabaseUtils.database.DatabaseService;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Profile("CreateNamespaceRole")
@Component
public class CreateNamespaceRoleRunner implements ApplicationRunner {
  @Value("${env.params.managedIdentityOid}")
  private String managedIdentityOid;

  @Value("${env.params.namespaceRole}")
  private String namespaceRole;

  @Value("${env.params.databaseNames}")
  private String databaseNames;

  private final DatabaseService databaseService;

  public CreateNamespaceRoleRunner(DatabaseService databaseService) {
    this.databaseService = databaseService;
  }

  @Override
  public void run(ApplicationArguments args) {
    databaseService.createNamespaceRole(
        namespaceRole, managedIdentityOid, Set.of(databaseNames.split(",")));
  }
}
