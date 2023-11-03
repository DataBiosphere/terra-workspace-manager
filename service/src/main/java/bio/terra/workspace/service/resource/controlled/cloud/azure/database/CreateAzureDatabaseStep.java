package bio.terra.workspace.service.resource.controlled.cloud.azure.database;

import static bio.terra.workspace.service.resource.controlled.cloud.azure.AzureUtils.getResourceName;

import bio.terra.cloudres.azure.resourcemanager.postgresflex.data.CreateDatabaseRequestData;
import bio.terra.common.iam.BearerToken;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.amalgam.landingzone.azure.LandingZoneApiDispatch;
import bio.terra.workspace.app.configuration.external.AzureConfiguration;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.workspace.WorkspaceService;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import bio.terra.workspace.service.workspace.model.AzureCloudContext;
import com.azure.core.management.exception.ManagementException;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Creates an Azure Database. Designed to run directly after {@link AzureDatabaseGuardStep}. */
public class CreateAzureDatabaseStep implements Step {

  private static final Logger logger = LoggerFactory.getLogger(CreateAzureDatabaseStep.class);
  private final AzureConfiguration azureConfig;
  private final CrlService crlService;
  private final ControlledAzureDatabaseResource resource;

  private final LandingZoneApiDispatch landingZoneApiDispatch;
  private final SamService samService;
  private final WorkspaceService workspaceService;
  private final UUID workspaceId;
  private final AzureDatabaseUtilsRunner azureDatabaseUtilsRunner;

  public CreateAzureDatabaseStep(
      AzureConfiguration azureConfig,
      CrlService crlService,
      ControlledAzureDatabaseResource resource,
      LandingZoneApiDispatch landingZoneApiDispatch,
      SamService samService,
      WorkspaceService workspaceService,
      UUID workspaceId,
      AzureDatabaseUtilsRunner azureDatabaseUtilsRunner) {
    this.azureConfig = azureConfig;
    this.crlService = crlService;
    this.resource = resource;
    this.landingZoneApiDispatch = landingZoneApiDispatch;
    this.samService = samService;
    this.workspaceService = workspaceService;
    this.workspaceId = workspaceId;
    this.azureDatabaseUtilsRunner = azureDatabaseUtilsRunner;
  }

  private String getPodName() {
    return "create-" + this.resource.getResourceId();
  }

  @Override
  public StepResult doStep(FlightContext context) throws InterruptedException, RetryException {
    var cloudContext =
        context
            .getWorkingMap()
            .get(ControlledResourceKeys.AZURE_CLOUD_CONTEXT, AzureCloudContext.class);
    var bearerToken = new BearerToken(samService.getWsmServiceAccountToken());
    var landingZoneId =
        landingZoneApiDispatch.getLandingZoneId(
            bearerToken, workspaceService.getWorkspace(workspaceId));
    var databaseResource =
        landingZoneApiDispatch
            .getSharedDatabase(bearerToken, landingZoneId)
            .orElseThrow(() -> new RuntimeException("No shared database found"));
    azureDatabaseUtilsRunner.createDatabaseWithDbRole(
        cloudContext, workspaceId, getPodName(), resource.getDatabaseName());
    crlService.recordAzureCleanup(
        CreateDatabaseRequestData.builder()
            .setTenantId(cloudContext.getAzureTenantId())
            .setSubscriptionId(cloudContext.getAzureSubscriptionId())
            .setResourceGroupName(cloudContext.getAzureResourceGroupId())
            .setServerName(getResourceName(databaseResource))
            .setDatabaseName(resource.getDatabaseName())
            .build());
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    final AzureCloudContext azureCloudContext =
        context
            .getWorkingMap()
            .get(ControlledResourceKeys.AZURE_CLOUD_CONTEXT, AzureCloudContext.class);

    var postgresManager = crlService.getPostgreSqlManager(azureCloudContext, azureConfig);
    var bearerToken = new BearerToken(samService.getWsmServiceAccountToken());
    UUID landingZoneId =
        landingZoneApiDispatch.getLandingZoneId(
            bearerToken, workspaceService.getWorkspace(workspaceId));
    var databaseResource =
        landingZoneApiDispatch
            .getSharedDatabase(bearerToken, landingZoneId)
            .orElseThrow(() -> new RuntimeException("No shared database found"));
    try {
      logger.info(
          "Attempting to delete database {} in server {} of resource group {}",
          resource.getDatabaseName(),
          getResourceName(databaseResource),
          azureCloudContext.getAzureResourceGroupId());

      postgresManager
          .databases()
          .delete(
              azureCloudContext.getAzureResourceGroupId(),
              getResourceName(databaseResource),
              resource.getDatabaseName());
      return StepResult.getStepResultSuccess();
    } catch (ManagementException e) {
      if (e.getResponse().getStatusCode() == 404) {
        logger.info(
            "Database {} in server {} of resource group {} not found",
            resource.getDatabaseName(),
            getResourceName(databaseResource),
            azureCloudContext.getAzureResourceGroupId());
        return StepResult.getStepResultSuccess();
      } else {
        throw e;
      }
    }
  }
}
