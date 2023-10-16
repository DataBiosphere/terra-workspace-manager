package bio.terra.workspace.azureDatabaseUtils.runners;

import bio.terra.workspace.azureDatabaseUtils.database.DatabaseService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Profile("PgDumpDatabase")
@Component
public class PgDumpDatabaseRunner implements ApplicationRunner {
    @Value("${env.params.newDbName}")  // comes from resources/application.yml
    private String newDbName;

    private final DatabaseService databaseService;

    public PgDumpDatabaseRunner(DatabaseService databaseService) {
        this.databaseService = databaseService;
    }

    @Override
    public void run(ApplicationArguments args) {
        databaseService.pgDump(newDbName);  // TODO: implement databaseService.pgDump
    }
}
