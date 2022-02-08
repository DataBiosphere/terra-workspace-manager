package bio.terra.workspace.service.resource.controlled.flight.create;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.db.ResourceDao;
import bio.terra.workspace.service.job.JobMapKeys;
import bio.terra.workspace.service.resource.controlled.model.ControlledResource;
import bio.terra.workspace.service.resource.model.WsmResource;

public class SetCreateResponseStep implements Step {
  private final ControlledResource resource;
  private final ResourceDao resourceDao;

  /**
   * This step retrieves the created resource from the database and returns it as the response.
   *
   * @param resource resource being created
   * @param resourceDao DAO for lookup
   */
  public SetCreateResponseStep(ControlledResource resource, ResourceDao resourceDao) {
    this.resource = resource;
    this.resourceDao = resourceDao;
  }

  @Override
  public StepResult doStep(FlightContext flightContext)
      throws InterruptedException, RetryException {
    WsmResource responseResource =
        resourceDao.getResource(resource.getWorkspaceId(), resource.getResourceId());
    flightContext.getWorkingMap().put(JobMapKeys.RESPONSE.getKeyName(), responseResource);
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) throws InterruptedException {
    return StepResult.getStepResultSuccess();
  }
}
