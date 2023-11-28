package bio.terra.workspace.service.resource.controlled.flight.clone.azure.database;

import static bio.terra.workspace.common.utils.FlightUtils.getRequired;
import static bio.terra.workspace.service.resource.controlled.cloud.azure.AzureUtils.getResourceName;
import static bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys.AZURE_CLOUD_CONTEXT;

import bio.terra.common.iam.BearerToken;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.amalgam.landingzone.azure.LandingZoneApiDispatch;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.job.JobMapKeys;
import bio.terra.workspace.service.resource.controlled.cloud.azure.AzureStorageAccessService;
import bio.terra.workspace.service.resource.controlled.cloud.azure.database.AzureDatabaseUtilsRunner;
import bio.terra.workspace.service.resource.controlled.cloud.azure.database.ControlledAzureDatabaseResource;
import bio.terra.workspace.service.resource.controlled.cloud.azure.storageContainer.ControlledAzureStorageContainerResource;
import bio.terra.workspace.service.workspace.WorkspaceService;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import bio.terra.workspace.service.workspace.model.AzureCloudContext;
import java.text.SimpleDateFormat;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DumpAzureDatabaseStep implements Step {
  private static final Logger logger = LoggerFactory.getLogger(DumpAzureDatabaseStep.class);

  private final ControlledAzureDatabaseResource sourceDatabase;
  private final LandingZoneApiDispatch landingZoneApiDispatch;
  private final SamService samService;
  private final WorkspaceService workspaceService;
  private final AzureStorageAccessService azureStorageAccessService;
  private final AzureDatabaseUtilsRunner azureDatabaseUtilsRunner;

  public DumpAzureDatabaseStep(
      ControlledAzureDatabaseResource sourceDatabase,
      LandingZoneApiDispatch landingZoneApiDispatch,
      SamService samService,
      WorkspaceService workspaceService,
      AzureStorageAccessService azureStorageAccessService,
      AzureDatabaseUtilsRunner azureDatabaseUtilsRunner) {
    this.sourceDatabase = sourceDatabase;
    this.landingZoneApiDispatch = landingZoneApiDispatch;
    this.samService = samService;
    this.workspaceService = workspaceService;
    this.azureStorageAccessService = azureStorageAccessService;
    this.azureDatabaseUtilsRunner = azureDatabaseUtilsRunner;
  }

  @Override
  public StepResult doStep(FlightContext context) throws InterruptedException, RetryException {
    var inputParameters = context.getInputParameters();
    var workingMap = context.getWorkingMap();
    var destinationWorkspaceId =
        inputParameters.get(
            WorkspaceFlightMapKeys.ControlledResourceKeys.DESTINATION_WORKSPACE_ID, UUID.class);
    var userRequest =
        getRequired(
            inputParameters,
            JobMapKeys.AUTH_USER_INFO.getKeyName(),
            AuthenticatedUserRequest.class);

    var destinationContainer =
        getRequired(
            workingMap,
            WorkspaceFlightMapKeys.ControlledResourceKeys.AZURE_STORAGE_CONTAINER,
            ControlledAzureStorageContainerResource.class);

    workingMap.put(
        WorkspaceFlightMapKeys.ControlledResourceKeys.AZURE_STORAGE_CONTAINER,
        destinationContainer);

    var sasToken =
        azureStorageAccessService.createAzureStorageContainerSasToken(
            destinationWorkspaceId, destinationContainer, userRequest, null, null, "rcw");
    var blobContainerUrlAuthenticated = sasToken.sasUrl();

    var blobFileName =
        String.format(
            "%s_%s.dump",
            sourceDatabase.getDatabaseName(),
            new SimpleDateFormat("yyyy-MM-dd_HH:mm:ss").format(new java.util.Date()));
    workingMap.put(WorkspaceFlightMapKeys.ControlledResourceKeys.CLONE_DB_DUMPFILE, blobFileName);

    // Query LZ for the postgres server info
    var bearerToken = new BearerToken(samService.getWsmServiceAccountToken());
    var landingZoneId =
        landingZoneApiDispatch.getLandingZoneId(
            bearerToken, workspaceService.getWorkspace(sourceDatabase.getWorkspaceId()));
    var dbServerName =
        getResourceName(
            landingZoneApiDispatch
                .getSharedDatabase(bearerToken, landingZoneId)
                .orElseThrow(() -> new RuntimeException("No shared database found")));
    var adminDbUserName =
        getResourceName(
            landingZoneApiDispatch
                .getSharedDatabaseAdminIdentity(bearerToken, landingZoneId)
                .orElseThrow(
                    () -> new RuntimeException("No shared database admin identity found")));

    logger.info(
        "running DumpAzureDatabaseStep with blobContainerName {} and blobFileName {}",
        destinationContainer.getStorageContainerName(),
        blobFileName);

    this.azureDatabaseUtilsRunner.pgDumpDatabase(
        workingMap.get(AZURE_CLOUD_CONTEXT, AzureCloudContext.class),
        sourceDatabase.getWorkspaceId(),
        "dump-db-" + this.sourceDatabase.getResourceId(),
        sourceDatabase.getDatabaseName(),
        dbServerName,
        adminDbUserName,
        blobFileName,
        destinationContainer.getStorageContainerName(),
        blobContainerUrlAuthenticated);

    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    // Nothing to undo in this step (dump file will be cleaned up by storage container deletion)
    return StepResult.getStepResultSuccess();
  }
}
