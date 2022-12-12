package bio.terra.workspace.service.resource.controlled.cloud.azure.storageContainer;

import static bio.terra.workspace.common.utils.FlightUtils.getRequired;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.db.ResourceDao;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
import java.util.UUID;

public class UpdateAzureStorageContainerRegionStep implements Step {

  private final ResourceDao resourceDao;
  private final UUID workspaceId;
  private final UUID resourceId;

  public UpdateAzureStorageContainerRegionStep(
      ResourceDao resourceDao, UUID workspaceId, UUID resourceId) {
    this.resourceDao = resourceDao;
    this.workspaceId = workspaceId;
    this.resourceId = resourceId;
  }

  @Override
  public StepResult doStep(FlightContext context) throws InterruptedException, RetryException {
    String region =
        getRequired(
            context.getWorkingMap(), ControlledResourceKeys.STORAGE_ACCOUNT_REGION, String.class);
    resourceDao.updateControlledResourceRegion(workspaceId, resourceId, region);
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    resourceDao.updateControlledResourceRegion(workspaceId, resourceId, /*region*/ null);
    return StepResult.getStepResultSuccess();
  }
}
