package bio.terra.workspace.service.resource.controlled.cloud.azure.vm;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.StepResult;
import bio.terra.workspace.app.configuration.external.AzureConfiguration;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.resource.controlled.cloud.azure.DeleteAzureControlledResourceStep;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import bio.terra.workspace.service.workspace.model.AzureCloudContext;
import com.azure.core.management.exception.ManagementException;
import com.azure.resourcemanager.compute.ComputeManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RemoveManagedIdentitiesAzureVmStep
    extends DeleteAzureControlledResourceStep<ControlledAzureVmResource> {
  private static final Logger logger =
      LoggerFactory.getLogger(RemoveManagedIdentitiesAzureVmStep.class);
  private final AzureConfiguration azureConfig;
  private final CrlService crlService;

  public RemoveManagedIdentitiesAzureVmStep(
      AzureConfiguration azureConfig, CrlService crlService, ControlledAzureVmResource resource) {
    super(resource);
    this.azureConfig = azureConfig;
    this.crlService = crlService;
  }

  @Override
  public StepResult deleteResource(FlightContext context) {
    final AzureCloudContext azureCloudContext =
        context
            .getWorkingMap()
            .get(ControlledResourceKeys.AZURE_CLOUD_CONTEXT, AzureCloudContext.class);

    ComputeManager computeManager = crlService.getComputeManager(azureCloudContext, azureConfig);
    var helperResult =
        AzureVmHelper.removeAllUserAssignedManagedIdentitiesFromVm(
            azureCloudContext, computeManager, resource.getVmName());
    if (helperResult.isSuccess()) {
      return helperResult;
    } else if (helperResult.getException().isPresent()
        && helperResult.getException().get() instanceof ManagementException) {
      throw (ManagementException) helperResult.getException().get();
    } else {
      return helperResult;
    }
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
