package bio.terra.workspace.service.resource.controlled.cloud.gcp.ainotebook;

import static bio.terra.workspace.common.utils.FlightUtils.getRequired;
import static bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys.CREATE_NOTEBOOK_REGION;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.db.ResourceDao;
import java.util.UUID;

public class UpdateNotebookRegionStep implements Step {

  private final ResourceDao resourceDao;
  private final UUID workspaceId;
  private final UUID resourceId;

  public UpdateNotebookRegionStep(ResourceDao resourceDao, UUID workspaceId, UUID resourceId) {
    this.resourceDao = resourceDao;
    this.workspaceId = workspaceId;
    this.resourceId = resourceId;
  }

  @Override
  public StepResult doStep(FlightContext context) throws InterruptedException, RetryException {
    String notebookRegion =
        getRequired(context.getWorkingMap(), CREATE_NOTEBOOK_REGION, String.class);
    resourceDao.updateControlledResourceRegion(workspaceId, resourceId, notebookRegion);
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    // The resource should get deleted eventually as the step is undone.
    resourceDao.updateControlledResourceRegion(workspaceId, resourceId, /*region=*/ null);
    return StepResult.getStepResultSuccess();
  }
}
