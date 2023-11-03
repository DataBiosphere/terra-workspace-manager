package bio.terra.workspace.service.resource.controlled.flight.clone.azure.database;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.service.resource.controlled.cloud.azure.database.AzureDatabaseUtilsRunner;
import bio.terra.workspace.service.workspace.model.AzureCloudContext;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RestoreAzureDatabaseStep implements Step {
  private static final Logger logger = LoggerFactory.getLogger(RestoreAzureDatabaseStep.class);

  private final AzureCloudContext azureCloudContext;
  private final AzureDatabaseUtilsRunner azureDatabaseUtilsRunner;
  private final UUID sourceWorkspaceId;
  private final UUID destinationWorkspaceId;
  private final String podName;
  private final String targetDbName;
  private final String dbServerName;
  private final String dbUserName;
  private final String blobFileName;
  private final String blobContainerName;
  private final String blobContainerUrlAuthenticated;

  public RestoreAzureDatabaseStep(
      AzureCloudContext azureCloudContext,
      AzureDatabaseUtilsRunner azureDatabaseUtilsRunner,
      UUID sourceWorkspaceId,
      UUID destinationWorkspaceId,
      String targetDbName,
      String dbServerName,
      String dbUserName,
      String blobFileName,
      String blobContainerUrlAuthenticated) {
    logger.info("(sanity check) DumpAzureDatabaseStep constructor has been called");
    this.azureCloudContext = azureCloudContext;
    this.azureDatabaseUtilsRunner = azureDatabaseUtilsRunner;
    this.sourceWorkspaceId = sourceWorkspaceId;
    this.destinationWorkspaceId = destinationWorkspaceId;
    this.podName = "restore-azure-db-step";
    this.targetDbName = targetDbName;
    this.dbServerName = dbServerName;
    this.dbUserName = dbUserName;
    this.blobFileName = blobFileName;
    this.blobContainerName = "sc-" + destinationWorkspaceId.toString();
    this.blobContainerUrlAuthenticated = blobContainerUrlAuthenticated;
  }

  @Override
  public StepResult doStep(FlightContext context) throws InterruptedException, RetryException {

    logger.info("<debug> context working map: {}", context.getWorkingMap());
    this.azureDatabaseUtilsRunner.pgRestoreDatabase(
        azureCloudContext,
        sourceWorkspaceId,
        podName,
        targetDbName,
        dbServerName,
        dbUserName,
        blobFileName,
        blobContainerName,
        blobContainerUrlAuthenticated);

    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    // TODO: delete the dumpfile
    return StepResult.getStepResultSuccess();
  }
}
