package bio.terra.workspace.service.resource.controlled.cloud.azure.disk;

import bio.terra.landingzone.library.landingzones.definition.factories.ManagedNetworkWithSharedResourcesFactory;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.workspace.app.configuration.external.AzureConfiguration;
import bio.terra.workspace.common.exception.AzureManagementExceptionUtils;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import bio.terra.workspace.service.workspace.model.AzureCloudContext;
import com.azure.core.management.exception.ManagementException;
import com.azure.resourcemanager.compute.ComputeManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A step for deleting a controlled Azure Disk resource. This step uses the following process to
 * actually delete the Azure Disk.
 */
public class DeleteAzureDiskStep implements Step {
  private static final Logger logger = LoggerFactory.getLogger(DeleteAzureDiskStep.class);
  private final AzureConfiguration azureConfig;
  private final CrlService crlService;
  private final ControlledAzureDiskResource resource;

  public DeleteAzureDiskStep(
      AzureConfiguration azureConfig, CrlService crlService, ControlledAzureDiskResource resource) {
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

    ComputeManager computeManager = crlService.getComputeManager(azureCloudContext, azureConfig);
    var azureResourceId =
        String.format(
            "/subscriptions/%s/resourceGroups/%s/providers/Microsoft.Compute/disks/%s",
            azureCloudContext.getAzureSubscriptionId(),
            azureCloudContext.getAzureResourceGroupId(),
            resource.getDiskName());
    try {
      logger.info("Attempting to delete disk " + azureResourceId);

      computeManager.disks().deleteById(azureResourceId);
      return StepResult.getStepResultSuccess();
    } catch (ManagementException ex) {
      logger.warn("Attempt to delete Azure disk failed on this try [resource_id={}]", azureResourceId, ex);
      if (AzureManagementExceptionUtils.isExceptionCode(ex, AzureManagementExceptionUtils.OPERATION_NOT_ALLOWED)) {
        // this error occurs when the parent VM is still running, no sense in retrying
        return new StepResult(StepStatus.STEP_RESULT_FAILURE_FATAL, ex);
      }
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY, ex);
    } catch (Exception ex) {
      logger.warn("Attempt to delete Azure disk failed on this try [resource_id={}]", azureResourceId, ex);
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY, ex);
    }
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) throws InterruptedException {
    logger.error(
        "Cannot undo delete of Azure disk resource {} in workspace {}.",
        resource.getResourceId(),
        resource.getWorkspaceId());
    return flightContext.getResult();
  }
}
