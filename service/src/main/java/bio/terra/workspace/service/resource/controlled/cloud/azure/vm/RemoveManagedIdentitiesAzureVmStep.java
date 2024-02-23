package bio.terra.workspace.service.resource.controlled.cloud.azure.vm;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.app.configuration.external.AzureConfiguration;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.resource.controlled.cloud.azure.DeleteAzureControlledResourceStep;
import bio.terra.workspace.service.resource.controlled.cloud.azure.managedIdentity.DeleteAzureManagedIdentityStep;
import bio.terra.workspace.service.resource.controlled.flight.delete.DeleteControlledResourceStep;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import bio.terra.workspace.service.workspace.model.AzureCloudContext;
import com.azure.resourcemanager.compute.ComputeManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RemoveManagedIdentitiesAzureVmStep extends DeleteAzureControlledResourceStep {
  private static final Logger logger =
      LoggerFactory.getLogger(RemoveManagedIdentitiesAzureVmStep.class);
  private final AzureConfiguration azureConfig;
  private final CrlService crlService;
  private final ControlledAzureVmResource resource;

  public RemoveManagedIdentitiesAzureVmStep(
      AzureConfiguration azureConfig, CrlService crlService, ControlledAzureVmResource resource) {
    this.azureConfig = azureConfig;
    this.crlService = crlService;
    this.resource = resource;
  }

  @Override
  public StepResult doStep(FlightContext context) throws InterruptedException, RetryException {
    final AzureCloudContext azureCloudContext =
        context
            .getWorkingMap()
            .get(ControlledResourceKeys.AZURE_CLOUD_CONTEXT, AzureCloudContext.class);

    ComputeManager computeManager = crlService.getComputeManager(azureCloudContext, azureConfig);
    return AzureVmHelper.removeAllUserAssignedManagedIdentitiesFromVm(
        azureCloudContext, computeManager, resource.getVmName());
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) throws InterruptedException {
    logger.error(
        "Cannot undo removing of user-assigned managed identities of Azure vm resource {} in workspace {}.",
        resource.getResourceId(),
        resource.getWorkspaceId());
    return flightContext.getResult();
  }
}
