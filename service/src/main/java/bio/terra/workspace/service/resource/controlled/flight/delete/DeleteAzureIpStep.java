package bio.terra.workspace.service.resource.controlled.flight.delete;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.workspace.app.configuration.external.AzureConfiguration;
import bio.terra.workspace.db.ResourceDao;
import bio.terra.workspace.service.crl.CrlService;
import bio.terra.workspace.service.resource.controlled.exception.BucketDeleteTimeoutException;
import bio.terra.workspace.service.resource.controlled.flight.create.CreateAzureIpStep;
import bio.terra.workspace.service.workspace.model.AzureCloudContext;
import com.azure.resourcemanager.compute.ComputeManager;
import com.google.cloud.storage.StorageException;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A step for deleting a controlled Azure IP resource. This step uses the following process to
 * actually delete the Azure IP: a. Set the lifecycle on the Azure IP to delete immediately b. Try
 * deleting the Azure IP c. If delete succeeds, finish step d. If delete fails, sleep one hour; goto
 * (either a or b; maybe a for belts and suspenders)
 *
 * <p>As this may take hours to days to complete, this step should never run as part of a
 * synchronous flight.
 */
// TODO: when Stairway implements timed waits, we can use those and not sit on a thread sleeping
//  for three days.
public class DeleteAzureIpStep implements Step {
  private static final int MAX_DELETE_TRIES = 72; // 3 days
  private static final Logger logger = LoggerFactory.getLogger(CreateAzureIpStep.class);
  private final AzureConfiguration azureConfig;
  private final ResourceDao resourceDao;
  private final CrlService crlService;
  private final AzureCloudContext azureCloudContext;

  private final UUID workspaceId;
  private final UUID resourceId;

  public DeleteAzureIpStep(
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
    int deleteTries = 0;

    boolean ipExists = true;

    var wsmResource = resourceDao.getResource(workspaceId, resourceId);
    var ip = wsmResource.castToControlledResource().castToAzureIpResource();

    while (ipExists) {
      ipExists = tryAzureIpDelete(ip.getIpName());
      if (ipExists) {
        TimeUnit.HOURS.sleep(1);
      }
      deleteTries++;
      if (deleteTries >= MAX_DELETE_TRIES) {
        // This will cause the flight to fail.
        throw new BucketDeleteTimeoutException(
            String.format("Failed to delete Azure IP after %d tries", MAX_DELETE_TRIES));
      }
    }
    return StepResult.getStepResultSuccess();
  }

  /**
   * Try deleting the Azure IP. It will fail if there are objects still in the Azure IP.
   *
   * @param azureIp AzureIp we should try to delete
   * @return Azure IP existence: true if the Azure IP still exists; false if we deleted it
   */
  private boolean tryAzureIpDelete(String azureIp) {
    ComputeManager computeManager = crlService.getComputeManager(azureCloudContext, azureConfig);
    var azureResourceId =
        String.format(
            "/subscriptions/%s/resourceGroups/%s/providers/Microsoft.Network/publicIPAddresses/%s",
            azureCloudContext.getAzureSubscriptionId(),
            azureCloudContext.getAzureResourceGroupId(),
            azureIp);

    try {
      logger.info("Attempting to delete IP " + azureResourceId);
      computeManager.networkManager().publicIpAddresses().deleteById(azureResourceId);
      return false;
    } catch (StorageException ex) {
      logger.info("Attempt to delete Azure IP failed on this try: " + azureResourceId, ex);
      return true;
    }
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) throws InterruptedException {
    logger.error(
        "Cannot undo delete of Azure IP resource {} in workspace {}.", resourceId, workspaceId);
    // Surface whatever error caused Stairway to begin undoing.
    return flightContext.getResult();
  }
}
