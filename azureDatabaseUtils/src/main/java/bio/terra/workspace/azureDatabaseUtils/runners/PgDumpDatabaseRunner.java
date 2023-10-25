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

    @Value("${env.db.connectToDatabase}")
    private String sourceDbName;

    @Value("${env.db.url}")
    private String sourceDbHost;

    @Value("${env.db.port}")
    private String sourceDbPort;

    @Value("${env.db.user}")
    private String sourceDbUser;

    @Value("${env.params.dumpfileName}")
    private String dumpfileName;

    @Value("${env.params.destinationWorkspaceId}")
    private String destinationWorkspaceId;

    @Value("${env.params.blobstorageDetails}")
    private String blobstorageDetails;

    private final DatabaseService databaseService;

    public PgDumpDatabaseRunner(DatabaseService databaseService) {
        this.databaseService = databaseService;
    }

    @Override
    public void run(ApplicationArguments args) {
        // should I reuse `newDbName`, or create a new param `cloneDbName`?
        databaseService.pgDump(
            sourceDbName, sourceDbHost, sourceDbPort, sourceDbUser, dumpfileName, destinationWorkspaceId, blobstorageDetails
        );
    }
}
