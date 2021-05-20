package bio.terra.workspace.service.workspace.flight;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.db.ResourceDao;
import bio.terra.workspace.service.workspace.model.CloudPlatform;
import java.util.UUID;

/**
 * A step to delete the controlled resources on a single cloud from a workspace.
 */
public class DeleteControlledDbResourcesStep implements Step {

  private final ResourceDao resourceDao;
  private final UUID workspaceId;
  private final CloudPlatform cloudPlatform;

  public DeleteControlledDbResourcesStep(
      ResourceDao resourceDao, UUID workspaceId, CloudPlatform cloudPlatform) {
    this.resourceDao = resourceDao;
    this.workspaceId = workspaceId;
    this.cloudPlatform = cloudPlatform;
  }

  @Override
  public StepResult doStep(FlightContext flightContext)
      throws InterruptedException, RetryException {
    resourceDao.deleteAllControlledResources(workspaceId, cloudPlatform);
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) throws InterruptedException {
    return flightContext.getResult();
  }
}
