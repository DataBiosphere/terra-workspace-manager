package bio.terra.workspace.service.resource.controlled.cloud.azure.database;

import static bio.terra.workspace.service.resource.controlled.cloud.azure.AzureUtils.getResourceName;

import bio.terra.common.iam.BearerToken;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.amalgam.landingzone.azure.LandingZoneApiDispatch;
import bio.terra.workspace.app.configuration.external.AzureConfiguration;
import bio.terra.workspace.common.exception.AzureManagementExceptionUtils;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.iam.SamService;
import bio.terra.workspace.service.resource.exception.DuplicateResourceException;
import bio.terra.workspace.service.workspace.WorkspaceService;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import bio.terra.workspace.service.workspace.model.AzureCloudContext;
import com.azure.core.management.exception.ManagementException;
import java.util.UUID;
import org.springframework.http.HttpStatus;

/**
 * Gets an Azure Database, and fails if it already exists. This step is designed to run immediately
 * before {@link CreateAzureDatabaseStep} to ensure idempotency of the create operation.
 */
public class AzureDatabaseGuardStep implements Step {
  private final AzureConfiguration azureConfig;
  private final CrlService crlService;
  private final ControlledAzureDatabaseResource resource;
  private final SamService samService;
  private final LandingZoneApiDispatch landingZoneApiDispatch;
  private final WorkspaceService workspaceService;
  private final UUID workspaceId;

  public AzureDatabaseGuardStep(
      AzureConfiguration azureConfig,
      CrlService crlService,
      ControlledAzureDatabaseResource resource,
      SamService samService,
      LandingZoneApiDispatch landingZoneApiDispatch,
      WorkspaceService workspaceService,
      UUID workspaceId) {
    this.azureConfig = azureConfig;
    this.crlService = crlService;
    this.resource = resource;
    this.samService = samService;
    this.landingZoneApiDispatch = landingZoneApiDispatch;
    this.workspaceService = workspaceService;
    this.workspaceId = workspaceId;
  }

  @Override
  public StepResult doStep(FlightContext context) throws InterruptedException, RetryException {
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
      postgresManager
          .databases()
          .get(
              azureCloudContext.getAzureResourceGroupId(),
              getResourceName(databaseResource),
              resource.getDatabaseName());
      return new StepResult(
          StepStatus.STEP_RESULT_FAILURE_FATAL,
          new DuplicateResourceException(
              String.format(
                  "An Azure Database with name %s already exists in resource group %s",
                  resource.getDatabaseName(), azureCloudContext.getAzureResourceGroupId())));
    } catch (ManagementException e) {
      if (e.getResponse().getStatusCode() == HttpStatus.NOT_FOUND.value()) {
        return StepResult.getStepResultSuccess();
      }
      return new StepResult(AzureManagementExceptionUtils.maybeRetryStatus(e), e);
    }
  }

  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    // Nothing to undo
    return StepResult.getStepResultSuccess();
  }
}
