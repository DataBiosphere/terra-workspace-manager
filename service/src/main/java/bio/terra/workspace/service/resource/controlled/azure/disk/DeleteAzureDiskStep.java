package bio.terra.workspace.service.resource.controlled.azure.disk;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.workspace.app.configuration.external.AzureConfiguration;
import bio.terra.workspace.db.ResourceDao;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.workspace.model.AzureCloudContext;
import com.azure.resourcemanager.compute.ComputeManager;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A step for deleting a controlled Azure Disk resource. This step uses the following process to
 * actually delete the Azure Disk.
 */
public class DeleteAzureDiskStep implements Step {
  private static final Logger logger = LoggerFactory.getLogger(DeleteAzureDiskStep.class);
  private final AzureConfiguration azureConfig;
  private final ResourceDao resourceDao;
  private final CrlService crlService;
  private final AzureCloudContext azureCloudContext;

  private final UUID workspaceId;
  private final UUID resourceId;

  public DeleteAzureDiskStep(
      AzureConfiguration azureConfig,
      AzureCloudContext azureCloudContext,
      CrlService crlService,
      ResourceDao resourceDao,
      UUID workspaceId,
      UUID resourceId) {
    this.crlService = crlService;
    this.resourceDao = resourceDao;
    this.azureCloudContext = azureCloudContext;
    this.azureConfig = azureConfig;
    this.workspaceId = workspaceId;
    this.resourceId = resourceId;
  }

  @Override
  public StepResult doStep(FlightContext context) throws InterruptedException {
    var wsmResource = resourceDao.getResource(workspaceId, resourceId);
    var disk = wsmResource.castToControlledResource().castToAzureDiskResource();

    ComputeManager computeManager = crlService.getComputeManager(azureCloudContext, azureConfig);
    var azureResourceId =
        String.format(
            "/subscriptions/%s/resourceGroups/%s/providers/Microsoft.Compute/disks/%s",
            azureCloudContext.getAzureSubscriptionId(),
            azureCloudContext.getAzureResourceGroupId(),
            disk.getDiskName());
    try {
      logger.info("Attempting to delete disk " + azureResourceId);

      computeManager.disks().deleteById(azureResourceId);
      return StepResult.getStepResultSuccess();
    } catch (Exception ex) {
      logger.info("Attempt to delete Azure disk failed on this try: " + azureResourceId, ex);
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY, ex);
    }
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) throws InterruptedException {
    logger.error(
        "Cannot undo delete of Azure disk resource {} in workspace {}.", resourceId, workspaceId);
    // Surface whatever error caused Stairway to begin undoing.
    return flightContext.getResult();
  }
}
