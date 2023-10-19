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

    @Value("${env.workflowCloning.sourceDbName}")
    private String sourceDbName;

    @Value("${env.workflowCloning.sourceDbHost}")
    private String sourceDbHost;

    @Value("${env.workflowCloning.sourceDbPort}")
    private String sourceDbPort;

    @Value("${env.workflowCloning.sourceDbUser}")
    private String sourceDbUser;

    @Value("${env.workflowCloning.pgDumpFilename}")
    private String pgDumpFilename;

    @Value("${env.workflowCloning.destinationWorkspaceId}")
    private String destinationWorkspaceId;

    @Value("${env.workflowCloning.blobstorageDetails}")
    private String blobstorageDetails;

    private final DatabaseService databaseService;

    public PgDumpDatabaseRunner(DatabaseService databaseService) {
        this.databaseService = databaseService;
    }

    @Override
    public void run(ApplicationArguments args) {
        // should I reuse `newDbName`, or create a new param `cloneDbName`?
        databaseService.pgDump(
            sourceDbName, sourceDbHost, sourceDbPort, sourceDbUser, pgDumpFilename, destinationWorkspaceId, blobstorageDetails
        );
    }
}
