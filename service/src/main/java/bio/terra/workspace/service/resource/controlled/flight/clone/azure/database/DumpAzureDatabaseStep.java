package bio.terra.workspace.service.resource.controlled.flight.clone.azure.database;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.common.utils.FlightUtils;
import bio.terra.workspace.service.resource.controlled.cloud.azure.database.AzureDatabaseUtilsRunner;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import bio.terra.workspace.service.workspace.model.AzureCloudContext;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

public class DumpAzureDatabaseStep implements Step {
    private static final Logger logger =
        LoggerFactory.getLogger(DumpAzureDatabaseStep.class);

    private final AzureCloudContext azureCloudContext;
    private final AzureDatabaseUtilsRunner azureDatabaseUtilsRunner;
    private final UUID sourceWorkspaceId; // = UUID.fromString("4d223fbc-7c6d-4621-9507-aff6eaf7b285");
    private final UUID destinationWorkspaceId; // = UUID.fromString("2d679bf4-87f1-41a9-ac84-ba67a0e59cd4");
    private final String podName; // = "dump-azure-db-step";
    private final String sourceDbName; // = "cbas_tssqab";
    private final String dbServerName; // = "lza138a62bcfd30fa5acfde4d025274b298c600b5c92dd07f59e7dd54dbf610";
    private final String dbUserName; // = "lze5edd91f8750312388";
    private final String blobFileName; // = sourceDbName + ".dump";
    private final String blobContainerName; // = "sc-" + destinationWorkspaceId.toString();
    private final String blobContainerUrlAuthenticated; // = "https://lzd2c1716ae9c046bed66d21.blob.core.windows.net/?sv=2021-12-02&spr=https&st=2023-11-02T15%3A06%3A02Z&se=2023-11-02T23%3A21%3A02Z&sr=c&sp=racwdlt&sig=4BbFJ2YgqKqlLhIuRR6FkK1NnptWqSWxt2xmgIKNbQM%3D&rscd=2693233942592e18a0c81";

    public DumpAzureDatabaseStep(
        AzureCloudContext azureCloudContext,
        AzureDatabaseUtilsRunner azureDatabaseUtilsRunner,
        UUID sourceWorkspaceId,
        UUID destinationWorkspaceId,
        String sourceDbName,
        String dbServerName,
        String dbUserName,
        String blobContainerUrlAuthenticated
        ) {
        logger.info(
            "(sanity check) DumpAzureDatabaseStep constructor has been called");
        this.azureCloudContext = azureCloudContext;
        this.azureDatabaseUtilsRunner = azureDatabaseUtilsRunner;
        this.sourceWorkspaceId = sourceWorkspaceId;
        this.destinationWorkspaceId = destinationWorkspaceId;
        this.podName = "dump-azure-db-step";
        this.sourceDbName = sourceDbName;
        this.dbServerName = dbServerName;
        this.dbUserName = dbUserName;
        this.blobFileName = sourceDbName + ".dump";
        this.blobContainerName = "sc-" + destinationWorkspaceId.toString();
        this.blobContainerUrlAuthenticated = blobContainerUrlAuthenticated;
    }

    @Override
    public StepResult doStep(FlightContext context)
        throws InterruptedException, RetryException {

        logger.info("<debug> context working map: {}", context.getWorkingMap());
        this.azureDatabaseUtilsRunner.pgDumpDatabase(
            azureCloudContext,
            sourceWorkspaceId,
            podName,
            sourceDbName,
            dbServerName,
            dbUserName,
            blobFileName,
            blobContainerName,
            blobContainerUrlAuthenticated
        );

        return StepResult.getStepResultSuccess();
    }

    @Override
    public StepResult undoStep(FlightContext context) throws InterruptedException {
        // TODO: delete the dumpfile
        return StepResult.getStepResultSuccess();
    }

    @Nullable
    private static AzureCloudContext getAzureCloudContext(FlightContext context) {
        return FlightUtils.getRequired(
            context.getWorkingMap(),
            WorkspaceFlightMapKeys.ControlledResourceKeys.AZURE_CLOUD_CONTEXT,
            AzureCloudContext.class);
    }

}
