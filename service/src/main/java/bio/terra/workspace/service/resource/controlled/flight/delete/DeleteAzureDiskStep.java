package bio.terra.workspace.service.resource.controlled.flight.delete;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.workspace.app.configuration.external.AzureConfiguration;
import bio.terra.workspace.db.ResourceDao;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.resource.controlled.flight.create.CreateAzureDiskStep;
import bio.terra.workspace.service.workspace.model.AzureCloudContext;
import com.azure.resourcemanager.compute.ComputeManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

/**
 * A step for deleting a controlled Azure Disk resource. This step uses the following process to
 * actually delete the Azure Disk: a. Set the lifecycle on the Azure Disk to delete immediately b.
 * Try deleting the Azure Disk c. If delete succeeds, finish step d. If delete fails, sleep one
 * hour; goto (either a or b; maybe a for belts and suspenders)
 *
 * <p>As this may take hours to days to complete, this step should never run as part of a
 * synchronous flight.
 */
// TODO: when Stairway implements timed waits, we can use those and not sit on a thread sleeping
//  for three days.
public class DeleteAzureDiskStep implements Step {
  private static final Logger logger = LoggerFactory.getLogger(CreateAzureDiskStep.class);
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
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_FATAL,ex);
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
