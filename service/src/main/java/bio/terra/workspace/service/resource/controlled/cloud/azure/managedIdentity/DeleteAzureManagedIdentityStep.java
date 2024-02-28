package bio.terra.workspace.service.resource.controlled.cloud.azure.managedIdentity;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.workspace.app.configuration.external.AzureConfiguration;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.resource.controlled.cloud.azure.DeleteAzureControlledResourceStep;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import bio.terra.workspace.service.workspace.model.AzureCloudContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A step for deleting a controlled Azure Managed Identity resource. This step uses the following
 * process to actually delete the Azure Managed Identity.
 */
public class DeleteAzureManagedIdentityStep extends DeleteAzureControlledResourceStep {
  private static final Logger logger =
      LoggerFactory.getLogger(DeleteAzureManagedIdentityStep.class);
  private final AzureConfiguration azureConfig;
  private final CrlService crlService;
  private final ControlledAzureManagedIdentityResource resource;

  public DeleteAzureManagedIdentityStep(
      AzureConfiguration azureConfig,
      CrlService crlService,
      ControlledAzureManagedIdentityResource resource) {
    this.crlService = crlService;
    this.azureConfig = azureConfig;
    this.resource = resource;
  }

  @Override
  public StepResult deleteResource(FlightContext context) {
    final AzureCloudContext azureCloudContext =
        context
            .getWorkingMap()
            .get(ControlledResourceKeys.AZURE_CLOUD_CONTEXT, AzureCloudContext.class);

    var msiManager = crlService.getMsiManager(azureCloudContext, azureConfig);
    var azureResourceId =
        String.format(
            "/subscriptions/%s/resourceGroups/%s/providers/Microsoft.ManagedIdentity/userAssignedIdentities/%s",
            azureCloudContext.getAzureSubscriptionId(),
            azureCloudContext.getAzureResourceGroupId(),
            resource.getManagedIdentityName());
    try {
      logger.info("Attempting to delete managed identity " + azureResourceId);

      msiManager.identities().deleteById(azureResourceId);
      return StepResult.getStepResultSuccess();
    } catch (Exception ex) {
      logger.info(
          "Attempt to delete Azure managed identity failed on this try: " + azureResourceId, ex);
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY, ex);
    }
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) throws InterruptedException {
    logger.error(
        "Cannot undo delete of Azure managed identity resource {} in workspace {}.",
        resource.getResourceId(),
        resource.getWorkspaceId());
    return flightContext.getResult();
  }
}
