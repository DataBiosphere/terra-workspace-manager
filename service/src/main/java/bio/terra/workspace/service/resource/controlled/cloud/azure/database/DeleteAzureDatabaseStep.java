package bio.terra.workspace.service.resource.controlled.cloud.azure.database;

import static bio.terra.workspace.service.resource.controlled.cloud.azure.AzureUtils.getResourceName;

import bio.terra.common.iam.BearerToken;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.StepResult;
import bio.terra.workspace.amalgam.landingzone.azure.LandingZoneApiDispatch;
import bio.terra.workspace.app.configuration.external.AzureConfiguration;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.resource.controlled.cloud.azure.DeleteAzureControlledResourceStep;
import bio.terra.workspace.service.workspace.WorkspaceService;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import bio.terra.workspace.service.workspace.model.AzureCloudContext;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A step for deleting a controlled Azure Database resource. This step uses the following process to
 * actually delete the Azure Database.
 */
public class DeleteAzureDatabaseStep
    extends DeleteAzureControlledResourceStep<ControlledAzureDatabaseResource> {
  private static final Logger logger = LoggerFactory.getLogger(DeleteAzureDatabaseStep.class);
  private final AzureConfiguration azureConfig;
  private final CrlService crlService;
  private final LandingZoneApiDispatch landingZoneApiDispatch;
  private final SamService samService;
  private final WorkspaceService workspaceService;
  private final UUID workspaceId;

  public DeleteAzureDatabaseStep(
      AzureConfiguration azureConfig,
      CrlService crlService,
      ControlledAzureDatabaseResource resource,
      LandingZoneApiDispatch landingZoneApiDispatch,
      SamService samService,
      WorkspaceService workspaceService,
      UUID workspaceId) {
    super(resource);
    this.crlService = crlService;
    this.azureConfig = azureConfig;
    this.landingZoneApiDispatch = landingZoneApiDispatch;
    this.samService = samService;
    this.workspaceService = workspaceService;
    this.workspaceId = workspaceId;
  }

  @Override
  public StepResult deleteResource(FlightContext context) {
    final AzureCloudContext azureCloudContext =
        context
            .getWorkingMap()
            .get(ControlledResourceKeys.AZURE_CLOUD_CONTEXT, AzureCloudContext.class);

    var postgresManager = crlService.getPostgreSqlManager(azureCloudContext, azureConfig);
    var bearerToken = new BearerToken(samService.getWsmServiceAccountToken());
    UUID landingZoneId =
        landingZoneApiDispatch.getLandingZoneId(
            bearerToken, workspaceService.getWorkspace(workspaceId));
    var databaseResource = landingZoneApiDispatch.getSharedDatabase(bearerToken, landingZoneId);

    if (databaseResource.isEmpty()) {
      return StepResult.getStepResultSuccess();
    }

    logger.info(
        "Attempting to delete database {} in server {} of resource group {}",
        resource.getDatabaseName(),
        getResourceName(databaseResource.get()),
        azureCloudContext.getAzureResourceGroupId());

    postgresManager
        .databases()
        .delete(
            azureCloudContext.getAzureResourceGroupId(),
            getResourceName(databaseResource.get()),
            resource.getDatabaseName());
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) throws InterruptedException {
    logger.error(
        "Cannot undo delete of Azure database resource {} in workspace {}.",
        resource.getResourceId(),
        resource.getWorkspaceId());
    return flightContext.getResult();
  }
}
