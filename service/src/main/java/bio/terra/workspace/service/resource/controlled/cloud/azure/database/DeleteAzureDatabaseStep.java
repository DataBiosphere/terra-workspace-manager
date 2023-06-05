package bio.terra.workspace.service.resource.controlled.cloud.azure.database;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.workspace.app.configuration.external.AzureConfiguration;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import bio.terra.workspace.service.workspace.model.AzureCloudContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A step for deleting a controlled Azure Database resource. This step uses the following process to
 * actually delete the Azure Database.
 */
public class DeleteAzureDatabaseStep implements Step {
  private static final Logger logger = LoggerFactory.getLogger(DeleteAzureDatabaseStep.class);
  private final AzureConfiguration azureConfig;
  private final CrlService crlService;
  private final ControlledAzureDatabaseResource resource;

  public DeleteAzureDatabaseStep(
      AzureConfiguration azureConfig, CrlService crlService, ControlledAzureDatabaseResource resource) {
    this.crlService = crlService;
    this.azureConfig = azureConfig;
    this.resource = resource;
  }

  @Override
  public StepResult doStep(FlightContext context) throws InterruptedException {
    final AzureCloudContext azureCloudContext =
        context
            .getWorkingMap()
            .get(ControlledResourceKeys.AZURE_CLOUD_CONTEXT, AzureCloudContext.class);

    var msiManager = crlService.getMsiManager(azureCloudContext, azureConfig);
    var azureResourceId =
        String.format(
            "/subscriptions/%s/resourceGroups/%s/providers/Microsoft.Database/userAssignedIdentities/%s",
            azureCloudContext.getAzureSubscriptionId(),
            azureCloudContext.getAzureResourceGroupId(),
            resource.getDatabaseName());
    try {
      logger.info("Attempting to delete database " + azureResourceId);

      msiManager.identities().deleteById(azureResourceId);
      return StepResult.getStepResultSuccess();
    } catch (Exception ex) {
      logger.info("Attempt to delete Azure database failed on this try: " + azureResourceId, ex);
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY, ex);
    }
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
