package bio.terra.workspace.service.resource.controlled.cloud.azure.disk;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.workspace.app.configuration.external.AzureConfiguration;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.resource.controlled.cloud.azure.DeleteAzureControlledResourceStep;
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
public class DeleteAzureDiskStep extends DeleteAzureControlledResourceStep {
  private static final Logger logger = LoggerFactory.getLogger(DeleteAzureDiskStep.class);
  private static final String DISK_ATTACHED_TO_VM_PARTIAL_ERROR_MSG = "is attached to VM";
  private static final String DISK_COULD_NOT_BE_DELETED_ERROR_CODE = "OperationNotAllowed";
  private final AzureConfiguration azureConfig;
  private final CrlService crlService;
  private final ControlledAzureDiskResource resource;

  public DeleteAzureDiskStep(
      AzureConfiguration azureConfig, CrlService crlService, ControlledAzureDiskResource resource) {
    super(resource);
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

    ComputeManager computeManager = crlService.getComputeManager(azureCloudContext, azureConfig);
    var azureResourceId =
        String.format(
            "/subscriptions/%s/resourceGroups/%s/providers/Microsoft.Compute/disks/%s",
            azureCloudContext.getAzureSubscriptionId(),
            azureCloudContext.getAzureResourceGroupId(),
            resource.getDiskName());

    computeManager.disks().deleteById(azureResourceId);
    return StepResult.getStepResultSuccess();
  }

  @Override
  protected StepResult handleResourceDeleteException(Exception e, FlightContext context) {
    if (e instanceof ManagementException ex && isDiskAttachedToVmError(ex)) {
      // we don't need to retry in this case since disk could not be deleted
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_FATAL, ex);
    }
    return super.handleResourceDeleteException(e, context);
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) throws InterruptedException {
    logger.error(
        "Cannot undo delete of Azure disk resource {} in workspace {}.",
        resource.getResourceId(),
        resource.getWorkspaceId());
    if (flightContext.getResult().getStepStatus().equals(StepStatus.STEP_RESULT_FAILURE_FATAL)
        && flightContext.getResult().getException().isPresent()
        && flightContext.getResult().getException().get() instanceof ManagementException e
        && isDiskAttachedToVmError(e)) {
      // disk has not been deleted, and we need to return success here to allow
      // DeleteMetadataStartStep successfully
      // complete undo operation to return WSM resource in READY state.
      return new StepResult(StepStatus.STEP_RESULT_SUCCESS);
    }
    return flightContext.getResult();
  }

  private boolean isDiskAttachedToVmError(ManagementException e) {
    return e.getValue().getCode().equals(DISK_COULD_NOT_BE_DELETED_ERROR_CODE)
        && e.getValue().getMessage().contains(DISK_ATTACHED_TO_VM_PARTIAL_ERROR_MSG);
  }
}
