package bio.terra.workspace.service.resource.controlled.cloud.azure.vm;

import static bio.terra.workspace.common.utils.FlightUtils.getRequired;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.db.ResourceDao;
import java.util.UUID;

public class UpdateVmRegionMetadataStep implements Step {

  private final ResourceDao resourceDao;
  private final UUID workspaceId;
  private final UUID resourceId;

  public UpdateVmRegionMetadataStep(ResourceDao resourceDao, UUID workspaceId, UUID resourceId) {
    this.resourceDao = resourceDao;
    this.workspaceId = workspaceId;
    this.resourceId = resourceId;
  }

  @Override
  public StepResult doStep(FlightContext context) throws InterruptedException, RetryException {
    String region =
        getRequired(
            context.getWorkingMap(), AzureVmHelper.WORKING_MAP_NETWORK_REGION, String.class);
    resourceDao.updateControlledResourceRegion(workspaceId, resourceId, region);
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    resourceDao.updateControlledResourceRegion(workspaceId, resourceId, /*region=*/ null);
    return StepResult.getStepResultSuccess();
  }
}
