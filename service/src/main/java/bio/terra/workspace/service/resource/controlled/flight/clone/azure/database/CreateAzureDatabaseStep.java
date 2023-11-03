package bio.terra.workspace.service.resource.controlled.flight.clone.azure.database;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.workspace.service.resource.controlled.cloud.azure.database.AzureDatabaseUtilsRunner;
import bio.terra.workspace.service.workspace.model.AzureCloudContext;

import java.util.UUID;

public class CreateAzureDatabaseStep implements Step {

    private final AzureCloudContext azureCloudContext;
    private final AzureDatabaseUtilsRunner azureDatabaseUtilsRunner;
    private final UUID sourceWorkspaceId;
    private final String podName;
    private final String databaseName;

    public CreateAzureDatabaseStep(AzureCloudContext azureCloudContext,
                                   AzureDatabaseUtilsRunner azureDatabaseUtilsRunner,
                                   UUID sourceWorkspaceId,
                                   String databaseName) {
        this.azureCloudContext = azureCloudContext;
        this.azureDatabaseUtilsRunner = azureDatabaseUtilsRunner;
        this.sourceWorkspaceId = sourceWorkspaceId;
        this.podName = "create-azure-db-step";
        this.databaseName = databaseName;
    }

    @Override
    public StepResult doStep(FlightContext context) throws InterruptedException {
        azureDatabaseUtilsRunner.createDatabaseWithDbRole(
            azureCloudContext,
            sourceWorkspaceId,
            podName,
            databaseName);

        return StepResult.getStepResultSuccess();
    }
    @Override
    public StepResult undoStep(FlightContext context) {
        return StepResult.getStepResultSuccess();
    }
}
