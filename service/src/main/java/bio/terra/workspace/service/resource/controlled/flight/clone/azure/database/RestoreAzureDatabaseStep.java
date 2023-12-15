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
import java.util.UUID;

public class RestoreAzureDatabaseStep implements Step {

  private final LandingZoneApiDispatch landingZoneApiDispatch;
  private final SamService samService;
  private final WorkspaceService workspaceService;
  private final AzureStorageAccessService azureStorageAccessService;
  private final AzureDatabaseUtilsRunner azureDatabaseUtilsRunner;

  public RestoreAzureDatabaseStep(
      LandingZoneApiDispatch landingZoneApiDispatch,
      SamService samService,
      WorkspaceService workspaceService,
      AzureStorageAccessService azureStorageAccessService,
      AzureDatabaseUtilsRunner azureDatabaseUtilsRunner) {

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

    ControlledAzureDatabaseResource destinationDatabase =
        getRequired(
            workingMap,
            WorkspaceFlightMapKeys.ControlledResourceKeys.CLONED_RESOURCE_DEFINITION,
            ControlledAzureDatabaseResource.class);

    var destinationWorkspaceId =
        getRequired(
            inputParameters,
            WorkspaceFlightMapKeys.ControlledResourceKeys.DESTINATION_WORKSPACE_ID,
            UUID.class);
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

    var sasToken =
        azureStorageAccessService.createAzureStorageContainerSasToken(
            destinationWorkspaceId, destinationContainer, userRequest, null, null, "rcwl");
    var blobContainerUrlAuthenticated = sasToken.sasUrl();

    var blobFileName =
        workingMap.get(
            WorkspaceFlightMapKeys.ControlledResourceKeys.CLONE_DB_DUMPFILE, String.class);

    // Query LZ for the postgres server info
    var bearerToken = new BearerToken(samService.getWsmServiceAccountToken());
    var landingZoneId =
        landingZoneApiDispatch.getLandingZoneId(
            bearerToken, workspaceService.getWorkspace(destinationDatabase.getWorkspaceId()));
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
    var dumpEncryptionKey =
        workingMap.get(
            WorkspaceFlightMapKeys.ControlledResourceKeys.CLONE_DB_DUMP_ENCRYPTION_KEY,
            String.class);

    this.azureDatabaseUtilsRunner.pgRestoreDatabase(
        workingMap.get(AZURE_CLOUD_CONTEXT, AzureCloudContext.class),
        destinationWorkspaceId,
        "restore-db-" + destinationDatabase.getResourceId(),
        destinationDatabase.getDatabaseName(),
        dbServerName,
        adminDbUserName,
        blobFileName,
        destinationContainer.getStorageContainerName(),
        blobContainerUrlAuthenticated,
        dumpEncryptionKey);

    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    // In theory, the proper "undo" action for this step would be to drop the database tables,
    // but leave the database intact. Instead, we rely on the fact that
    // CopyControlledAzureDatabaseDefinitionStep.undoStep() deletes the entire database -
    // so anytime this step would be undone, the database will be deleted anyway.
    return StepResult.getStepResultSuccess();
  }
}
