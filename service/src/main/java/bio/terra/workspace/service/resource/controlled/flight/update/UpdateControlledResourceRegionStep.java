package bio.terra.workspace.service.resource.controlled.flight.update;

import static bio.terra.workspace.common.utils.FlightUtils.getRequired;
import static bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys.CREATE_RESOURCE_REGION;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.db.ResourceDao;
import java.util.UUID;

/**
 * A step to update the resource region as part of the resource creation. Some WSM resource computes
 * the region as a step of resource creation flight. We update the resource table at the end of the
 * cloud instance creation to have the most accurate region field.
 */
public class UpdateControlledResourceRegionStep implements Step {

  private final ResourceDao resourceDao;
  private final UUID resourceId;

  public UpdateControlledResourceRegionStep(ResourceDao resourceDao, UUID resourceId) {
    this.resourceDao = resourceDao;
    this.resourceId = resourceId;
  }

  @Override
  public StepResult doStep(FlightContext context) throws InterruptedException, RetryException {
    String region = getRequired(context.getWorkingMap(), CREATE_RESOURCE_REGION, String.class);
    resourceDao.updateControlledResourceRegion(resourceId, region);
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext context) throws InterruptedException {
    // The resource should get deleted eventually as the step is undone.
    resourceDao.updateControlledResourceRegion(resourceId, /* region= */ null);
    return StepResult.getStepResultSuccess();
  }
}
